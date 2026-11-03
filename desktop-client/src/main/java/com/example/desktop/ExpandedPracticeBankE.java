package com.example.desktop;

import java.util.ArrayList;
import java.util.List;

final class ExpandedPracticeBankE {
    private ExpandedPracticeBankE() {}
    static List<PracticeQuestionBank.Question> all() {
        List<PracticeQuestionBank.Question> q = new ArrayList<>();
        q.addAll(ExpandedPracticeBankE1.all());
        q.addAll(ExpandedPracticeBankE2.all());
        q.addAll(ExpandedPracticeBankE3.all());
        return List.copyOf(q);
    }
}
