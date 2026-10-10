package com.ved.study.learn;

import java.util.Map;

import com.ved.knowledge.figure.FigureView;

public record AskResponse(String answer, boolean grounded, Map<String, FigureView> figures) {
}
