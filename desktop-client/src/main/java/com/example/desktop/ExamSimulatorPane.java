package com.example.desktop;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.util.Duration;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Consumer;

/** Blueprint-balanced 309A / Construction Electrician practice exam simulator. */
public final class ExamSimulatorPane extends BorderPane {
    private static final String PRIMARY="-fx-background-color:#0e7490;-fx-text-fill:white;-fx-font-weight:bold;-fx-background-radius:8;-fx-padding:9 13;";
    private static final String SECONDARY="-fx-background-color:#f8fafc;-fx-text-fill:#0f4c81;-fx-border-color:#c7d9e8;-fx-border-radius:8;-fx-background-radius:8;-fx-padding:8 11;-fx-font-weight:bold;";
    private static final String CARD="-fx-background-color:white;-fx-background-radius:14;-fx-border-color:#dfe7ef;-fx-border-radius:14;-fx-padding:16;";

    private final ObjectMapper json;
    private final LocalWorkspaceStore store;
    private final Consumer<String> openTool;
    private final List<PracticeQuestionBank.Question> bank = ExpandedPracticeBank.combined();
    private List<PracticeQuestionBank.Question> exam = new ArrayList<>();
    private int[] answers = new int[0];
    private int index = 0;
    private int remainingSeconds = 0;
    private Timeline timer;
    private boolean finished = false;

    private final ChoiceBox<String> mode = new ChoiceBox<>();
    private final CheckBox studyMode = new CheckBox("Study mode — show feedback as I answer");
    private final Label timerLabel = new Label("00:00:00");
    private final Label progress = new Label("No exam started");
    private final Label category = new Label();
    private final Label question = new Label();
    private final ToggleGroup choices = new ToggleGroup();
    private final RadioButton[] optionButtons = new RadioButton[4];
    private final TextArea feedback = new TextArea();
    private final TextArea review = new TextArea();
    private final Button prev = secondary("← Previous");
    private final Button next = secondary("Next →");
    private final Button grade = primary("Finish & grade exam");
    private final Button openRecommended = secondary("Open recommended tool");
    private String recommendedTool = "Integrated Design";

    public ExamSimulatorPane(ObjectMapper json, String username, Consumer<String> openTool) {
        this.json = json;
        this.store = new LocalWorkspaceStore(json, username);
        this.openTool = openTool == null ? s -> {} : openTool;

        VBox page = new VBox(13);
        page.setPadding(new Insets(2));
        Label title = new Label("309A / Red Seal Exam Simulator");
        title.setStyle("-fx-font-size:22px;-fx-font-weight:bold;-fx-text-fill:#0f172a;");
        Label sub = new Label("Uses the expanded local practice bank and Construction Electrician major-work-activity weighting. Full mode uses A 11 / B 28 / C 30 / D 21 / E 10; 50-question mode uses A 6 / B 14 / C 15 / D 10 / E 5; Quick 25 uses five from each category.");
        sub.setWrapText(true); sub.setStyle("-fx-text-fill:#64748b;");
        page.getChildren().addAll(title,sub);

        mode.getItems().addAll("Full 100 — 4 hours","Practice 50 — 2 hours","Quick 25 — 1 hour");
        mode.setValue("Full 100 — 4 hours");
        Button start = primary("Start new exam");
        Button resumeMissed = secondary("Retry missed questions");
        resumeMissed.setDisable(true);
        HBox setupRow = new HBox(10,new Label("Mode:"),mode,start,studyMode,new Region(),new Label("Time:"),timerLabel);
        HBox.setHgrow(setupRow.getChildren().get(4), Priority.ALWAYS);
        setupRow.setAlignment(Pos.CENTER_LEFT);
        VBox setup = card("1. Exam setup","A new exam samples without replacement from the combined bank. Category weighting is enforced before questions are shown.");
        setup.getChildren().addAll(setupRow, progress);

        question.setWrapText(true);
        question.setStyle("-fx-font-size:17px;-fx-font-weight:bold;-fx-text-fill:#0f172a;");
        category.setStyle("-fx-text-fill:#0e7490;-fx-font-weight:bold;");
        VBox options = new VBox(9);
        for (int i=0;i<4;i++) {
            RadioButton rb = new RadioButton();
            rb.setToggleGroup(choices); rb.setUserData(i); rb.setWrapText(true);
            rb.setMaxWidth(Double.MAX_VALUE); rb.setStyle("-fx-padding:8;-fx-font-size:14px;");
            final int pick=i;
            rb.setOnAction(e -> { if (!finished && answers.length>index) { answers[index]=pick; if (studyMode.isSelected()) showInstantFeedback(); updateProgress(); } });
            optionButtons[i]=rb; options.getChildren().add(rb);
        }
        feedback.setEditable(false); feedback.setWrapText(true); feedback.setPrefRowCount(5);
        feedback.setStyle("-fx-control-inner-background:#f8fafc;-fx-font-size:12px;");
        feedback.setVisible(false); feedback.setManaged(false);
        prev.setOnAction(e->{saveSelection();if(index>0){index--;render();}});
        next.setOnAction(e->{saveSelection();if(index+1<exam.size()){index++;render();}});
        grade.setOnAction(e->finishExam(false));
        HBox nav = new HBox(8,prev,next,new Region(),grade); HBox.setHgrow(nav.getChildren().get(2),Priority.ALWAYS);
        VBox qCard = card("2. Question","Answer A–D. You can move backward and forward; answers are retained. In normal exam mode, explanations stay hidden until grading.");
        qCard.getChildren().addAll(category,question,options,feedback,nav);

        review.setEditable(false); review.setWrapText(true); review.setPrefRowCount(22);
        review.setStyle("-fx-font-family:Consolas;-fx-font-size:12px;-fx-control-inner-background:#0f172a;-fx-text-fill:#e2e8f0;");
        Button copy = secondary("Copy review");
        copy.setOnAction(e->{javafx.scene.input.ClipboardContent c=new javafx.scene.input.ClipboardContent();c.putString(review.getText());javafx.scene.input.Clipboard.getSystemClipboard().setContent(c);});
        openRecommended.setDisable(true); openRecommended.setOnAction(e->openTool.accept(recommendedTool));
        VBox results = card("3. Results & targeted review","After grading, this shows overall/category scores, every missed question, the correct answer, explanation/reference, and the calculator area that best matches the weakest category.");
        results.getChildren().addAll(new HBox(8,openRecommended,resumeMissed,copy),review);

        start.setOnAction(e->startExam(mode.getValue()));
        resumeMissed.setOnAction(e->{
            List<PracticeQuestionBank.Question> missed=new ArrayList<>();
            for(int i=0;i<exam.size();i++) if(answers[i]!=exam.get(i).correctIndex()) missed.add(exam.get(i));
            if(missed.isEmpty()) return;
            startSpecific(missed, Math.max(15*60,missed.size()*120));
        });
        review.textProperty().addListener((obs,o,n)->resumeMissed.setDisable(!finished || countMissed()==0));

        page.getChildren().addAll(setup,qCard,results);
        ScrollPane scroll=new ScrollPane(page); scroll.setFitToWidth(true); scroll.setStyle("-fx-background:transparent;-fx-background-color:transparent;");
        setCenter(scroll);
        setExamControls(false);
    }

    private void startExam(String selectedMode) {
        int[] counts;
        int seconds;
        if (selectedMode.startsWith("Full 100")) { counts=new int[]{11,28,30,21,10}; seconds=4*60*60; }
        else if (selectedMode.startsWith("Practice 50")) { counts=new int[]{6,14,15,10,5}; seconds=2*60*60; }
        else { counts=new int[]{5,5,5,5,5}; seconds=60*60; }
        List<PracticeQuestionBank.Question> selected=new ArrayList<>();
        Random rnd=new Random(System.nanoTime());
        for(int i=0;i<5;i++) {
            char code=(char)('A'+i);
            List<PracticeQuestionBank.Question> pool=new ArrayList<>();
            for(PracticeQuestionBank.Question q:bank) if(categoryCode(q)==code) pool.add(q);
            Collections.shuffle(pool,rnd);
            if(pool.size()<counts[i]) throw new IllegalStateException("Not enough category "+code+" questions in the local bank.");
            selected.addAll(pool.subList(0,counts[i]));
        }
        startSpecific(selected,seconds);
    }

    private void startSpecific(List<PracticeQuestionBank.Question> questions,int seconds) {
        exam=new ArrayList<>(questions);
        answers=new int[exam.size()]; Arrays.fill(answers,-1);
        index=0; finished=false; remainingSeconds=seconds; review.clear(); feedback.clear();
        openRecommended.setDisable(true); setExamControls(true); startTimer(); render();
    }

    private void startTimer() {
        if(timer!=null) timer.stop();
        updateTimer();
        timer=new Timeline(new KeyFrame(Duration.seconds(1),e->{
            if(finished) return;
            remainingSeconds=Math.max(0,remainingSeconds-1); updateTimer();
            if(remainingSeconds==0) finishExam(true);
        }));
        timer.setCycleCount(Timeline.INDEFINITE); timer.play();
    }

    private void updateTimer() {
        int h=remainingSeconds/3600,m=(remainingSeconds%3600)/60,s=remainingSeconds%60;
        timerLabel.setText(String.format(Locale.CANADA,"%02d:%02d:%02d",h,m,s));
        timerLabel.setStyle(remainingSeconds<=600?"-fx-text-fill:#b91c1c;-fx-font-weight:bold;":"-fx-text-fill:#0f172a;-fx-font-weight:bold;");
    }

    private void render() {
        if(exam.isEmpty()) return;
        PracticeQuestionBank.Question q=exam.get(index);
        category.setText("Category "+categoryCode(q)+" · Question "+(index+1)+" of "+exam.size());
        question.setText(q.question());
        choices.selectToggle(null);
        for(int i=0;i<4;i++) {
            optionButtons[i].setText((char)('A'+i)+". "+q.options().get(i));
            optionButtons[i].setDisable(finished);
            if(answers[index]==i) choices.selectToggle(optionButtons[i]);
        }
        prev.setDisable(index==0); next.setDisable(index+1>=exam.size());
        feedback.setVisible(studyMode.isSelected()&&answers[index]>=0&&!finished);
        feedback.setManaged(feedback.isVisible());
        if(feedback.isVisible()) showInstantFeedback(); else feedback.clear();
        updateProgress();
    }

    private void saveSelection() {
        Toggle t=choices.getSelectedToggle();
        if(!finished && t!=null) answers[index]=(Integer)t.getUserData();
    }

    private void showInstantFeedback() {
        if(exam.isEmpty()||answers[index]<0) return;
        PracticeQuestionBank.Question q=exam.get(index);
        boolean ok=answers[index]==q.correctIndex();
        feedback.setText((ok?"Correct. ":"Not correct. Correct answer: "+(char)('A'+q.correctIndex())+". ")+q.explanation()+"\nReference: "+q.reference());
        feedback.setVisible(true); feedback.setManaged(true);
    }

    private void updateProgress() {
        if(exam.isEmpty()){progress.setText("No exam started");return;}
        int answered=0;for(int a:answers)if(a>=0)answered++;
        progress.setText("Answered "+answered+" / "+exam.size()+" · Remaining "+(exam.size()-answered)+" · Bank size "+bank.size());
    }

    private void finishExam(boolean timeExpired) {
        if(finished||exam.isEmpty()) return;
        saveSelection(); finished=true; if(timer!=null) timer.stop();
        int correct=0; Map<Character,int[]> stats=new LinkedHashMap<>();
        for(char c='A';c<='E';c++)stats.put(c,new int[]{0,0});
        StringBuilder missed=new StringBuilder();
        for(int i=0;i<exam.size();i++) {
            PracticeQuestionBank.Question q=exam.get(i); char c=categoryCode(q); stats.get(c)[1]++;
            if(answers[i]==q.correctIndex()){correct++;stats.get(c)[0]++;}
            else {
                missed.append(String.format(Locale.CANADA,"%nQ%d [%s] %s%n",i+1,c,q.question()));
                missed.append("Your answer: ").append(answers[i]<0?"(blank)":String.valueOf((char)('A'+answers[i]))).append('\n');
                missed.append("Correct: ").append((char)('A'+q.correctIndex())).append(". ").append(q.options().get(q.correctIndex())).append('\n');
                missed.append("Why: ").append(q.explanation()).append('\n');
                missed.append("Reference: ").append(q.reference()).append("\n");
            }
        }
        double pct=100.0*correct/exam.size(); char weak='A';double weakPct=101;
        StringBuilder cat=new StringBuilder();
        for(char c='A';c<='E';c++){int[] s=stats.get(c);double p=s[1]==0?100:100.0*s[0]/s[1];cat.append(String.format(Locale.CANADA,"Category %s: %d/%d (%.1f%%)%n",c,s[0],s[1],p));if(s[1]>0&&p<weakPct){weakPct=p;weak=c;}}
        recommendedTool=toolFor(weak);
        String summary=String.format(Locale.CANADA,"EXAM RESULT — %s%n%nScore: %d / %d = %.1f%%%nResult: %s%nTime: %s%n%nCATEGORY BREAKDOWN%n%s%nWeakest category: %s (%.1f%%)%nRecommended Workbench area: %s%n%nMISSED / BLANK QUESTIONS%n%s",timeExpired?"TIME EXPIRED":"SUBMITTED",correct,exam.size(),pct,pct>=70?"PRACTICE PASS":"BELOW 70% PRACTICE TARGET",timerLabel.getText(),cat,weak,weakPct,recommendedTool,missed.length()==0?"None — perfect score.":missed.toString());
        review.setText(summary); openRecommended.setText("Open "+recommendedTool); openRecommended.setDisable(false);
        for(RadioButton rb:optionButtons)rb.setDisable(true); grade.setDisable(true); next.setDisable(true); prev.setDisable(true);
        progress.setText("Finished · "+correct+" / "+exam.size()+" correct ("+String.format(Locale.CANADA,"%.1f",pct)+"%)");
        ObjectNode rec=json.createObjectNode();rec.put("saved", LocalDateTime.now().toString());rec.put("mode",mode.getValue());rec.put("questions",exam.size());rec.put("correct",correct);rec.put("percent",pct);rec.put("weakestCategory",String.valueOf(weak));rec.put("recommendedTool",recommendedTool);rec.put("timeExpired",timeExpired);store.append("quizHistory",rec);
    }

    private int countMissed(){if(!finished)return 0;int n=0;for(int i=0;i<exam.size();i++)if(answers[i]!=exam.get(i).correctIndex())n++;return n;}
    private void setExamControls(boolean enabled){for(RadioButton rb:optionButtons)rb.setDisable(!enabled);prev.setDisable(!enabled);next.setDisable(!enabled);grade.setDisable(!enabled);question.setText(enabled?question.getText():"Start an exam to load the first question.");category.setText(enabled?category.getText():"");}
    private static char categoryCode(PracticeQuestionBank.Question q){String s=q.category();return s==null||s.isBlank()?'?':Character.toUpperCase(s.charAt(0));}
    private static String toolFor(char c){return switch(c){case 'A'->"Guided Worksheets";case 'B'->"Integrated Design";case 'C'->"Raceway + Derating";case 'D'->"Integrated Design";case 'E'->"CEC Sections / Advanced";default->"Integrated Design";};}

    private VBox card(String title,String desc){VBox b=new VBox(10);b.setStyle(CARD);Label h=new Label(title);h.setStyle("-fx-font-size:17px;-fx-font-weight:bold;-fx-text-fill:#0f172a;");Label d=new Label(desc);d.setWrapText(true);d.setStyle("-fx-text-fill:#64748b;");b.getChildren().addAll(h,d);return b;}
    private static Button primary(String s){Button b=new Button(s);b.setStyle(PRIMARY);return b;}
    private static Button secondary(String s){Button b=new Button(s);b.setStyle(SECONDARY);return b;}
}
