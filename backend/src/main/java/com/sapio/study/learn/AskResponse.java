package com.sapio.study.learn;

import java.util.Map;

import com.sapio.knowledge.figure.FigureView;

public record AskResponse(String answer, boolean grounded, Map<String, FigureView> figures) {
}
