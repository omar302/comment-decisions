package dev.ot.comment_decisions;

import java.util.Map;

import org.springframework.ai.decision.DecisionModel;
import org.springframework.ai.decision.DecisionResponse;
import org.springframework.ai.decision.answer.YesNoAnswer;
import org.springframework.ai.decision.question.ChoiceQuestion;
import org.springframework.ai.decision.question.ScoreQuestion;
import org.springframework.ai.decision.question.YesNoQuestion;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController 
public class ModerationController {

    // 1. Yes or No
    private static final YesNoQuestion SPAM = YesNoQuestion.of("Is this comment spam or self-promotion");

    // 2. Pick one option
    private static final ChoiceQuestion TOPIC =
            ChoiceQuestion.of("What kind of comment is this?", "Question", "Feedback", "Off topic");
    
    // 3. A level on a scale, from lowest to highest
    private static final ScoreQuestion TONE =
            ScoreQuestion.of("How respectful is this comment?", "Rude", "Neutral", "Kind");
    
    private final DecisionModel decisionModel;

    public ModerationController(DecisionModel decisionModel) {
        this.decisionModel = decisionModel;
    }

    @PostMapping("/comments")
    public Moderation moderate(@RequestBody Comment comment) {
        // One call to Jev answers all three questions
        DecisionResponse response = decisionModel.call(comment.text(),
                Map.of("spam", SPAM, "topic", TOPIC, "tone", TONE));

        // Jev can refuse to answer. Then a person should decide.
        if (response.isRefused("spam") || response.isRefused("topic") || response.isRefused("tone")) {
            return new Moderation(Action.HOLD, "Jev did not answer", null, null, null);
        }

        YesNoAnswer spam = response.getYesNo("spam");
        String topic = response.getChoice("topic").getValue();
        String tone = TONE.getLevelLabels().get(response.getScore("tone").getNearestLevel());

        // The answers are typed, so the rules are plain Java. You pick the lines, not the model.
        if (spam.isYes(0.9)) {
            return new Moderation(Action.REJECT, "Spam", spam.getProbability(), topic, tone);
        }
        if (spam.isYes(0.5)) {
            return new Moderation(Action.HOLD, "Might be spam", spam.getProbability(), topic, tone);
        }
        if (tone.equals("Rude")) {
            return new Moderation(Action.HOLD, "Rude", spam.getProbability(), topic, tone);
        }
        return new Moderation(Action.PUBLISH, "Looks good", spam.getProbability(), topic, tone);
    }

    public record Comment(String text) {
    }

    public enum Action {
        PUBLISH, HOLD, REJECT
    }

    public record Moderation(Action action, String reason, Double spamProbability, String topic, String tone) {
    }

}
