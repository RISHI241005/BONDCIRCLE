package com.datingapp.chat.replycoach.dto;

public class ConversationStateDto {

    private String topic;
    private String tone;
    private String engagement;
    private String language;
    private boolean dry;
    private String stage;
    private boolean hasUnansweredQuestion;
    private String unansweredQuestionText;
    private String momentum;
    private String direction;
    private String responseExpectation;
    private String depth;

    public ConversationStateDto() {
    }

    public ConversationStateDto(String topic, String tone, String engagement, String language, boolean dry) {
        this.topic = topic;
        this.tone = tone;
        this.engagement = engagement;
        this.language = language;
        this.dry = dry;
        this.stage = dry ? "DRY" : "CASUAL";
    }

    public ConversationStateDto(
            String topic,
            String tone,
            String engagement,
            String language,
            boolean dry,
            String stage,
            boolean hasUnansweredQuestion,
            String unansweredQuestionText) {
        this.topic = topic;
        this.tone = tone;
        this.engagement = engagement;
        this.language = language;
        this.dry = dry;
        this.stage = stage;
        this.hasUnansweredQuestion = hasUnansweredQuestion;
        this.unansweredQuestionText = unansweredQuestionText;
    }

    public ConversationStateDto(
            String topic,
            String tone,
            String engagement,
            String language,
            boolean dry,
            String stage,
            boolean hasUnansweredQuestion,
            String unansweredQuestionText,
            String momentum,
            String direction,
            String responseExpectation,
            String depth) {
        this(topic, tone, engagement, language, dry, stage, hasUnansweredQuestion, unansweredQuestionText);
        this.momentum = momentum;
        this.direction = direction;
        this.responseExpectation = responseExpectation;
        this.depth = depth;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public String getTone() {
        return tone;
    }

    public void setTone(String tone) {
        this.tone = tone;
    }

    public String getEngagement() {
        return engagement;
    }

    public void setEngagement(String engagement) {
        this.engagement = engagement;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public boolean isDry() {
        return dry;
    }

    public void setDry(boolean dry) {
        this.dry = dry;
    }

    public String getStage() {
        return stage;
    }

    public void setStage(String stage) {
        this.stage = stage;
    }

    public boolean isHasUnansweredQuestion() {
        return hasUnansweredQuestion;
    }

    public boolean hasUnansweredQuestion() {
        return hasUnansweredQuestion;
    }

    public void setHasUnansweredQuestion(boolean hasUnansweredQuestion) {
        this.hasUnansweredQuestion = hasUnansweredQuestion;
    }

    public String getUnansweredQuestionText() {
        return unansweredQuestionText;
    }

    public void setUnansweredQuestionText(String unansweredQuestionText) {
        this.unansweredQuestionText = unansweredQuestionText;
    }

    public String getMomentum() {
        return momentum;
    }

    public void setMomentum(String momentum) {
        this.momentum = momentum;
    }

    public String getDirection() {
        return direction;
    }

    public void setDirection(String direction) {
        this.direction = direction;
    }

    public String getResponseExpectation() {
        return responseExpectation;
    }

    public void setResponseExpectation(String responseExpectation) {
        this.responseExpectation = responseExpectation;
    }

    public String getDepth() {
        return depth;
    }

    public void setDepth(String depth) {
        this.depth = depth;
    }
}
