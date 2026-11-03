package com.example.desktop;

import java.util.ArrayList;
import java.util.List;

public final class ExpandedPracticeBank {
    private ExpandedPracticeBank() {}

    public static List<PracticeQuestionBank.Question> all() {
        List<PracticeQuestionBank.Question> q = new ArrayList<>();
        q.addAll(ExpandedPracticeBankA.all());
        q.addAll(ExpandedPracticeBankB.all());
        q.addAll(ExpandedPracticeBankC.all());
        q.addAll(ExpandedPracticeBankD.all());
        q.addAll(ExpandedPracticeBankE.all());
        return List.copyOf(q);
    }

    public static List<PracticeQuestionBank.Question> combined() {
        List<PracticeQuestionBank.Question> q = new ArrayList<>(PracticeQuestionBank.all());
        q.addAll(all());
        return List.copyOf(q);
    }
}
