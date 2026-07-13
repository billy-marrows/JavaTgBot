package Bot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

enum QuestionType {
    OPENED, CLOSED, MULTIPLE
}

class TestQuestion {
    UUID id;
    QuestionType type;
    String description;
    List<String> variants = new ArrayList<>();
    
    public TestQuestion(QuestionType type, String description) {
        this.id = UUID.randomUUID();
        this.type = type;
        this.description = description;
    }
}

class PollSession {
    UUID pollId;
    int currentQuestionIndex = 0;
    List<TestQuestion> questions;
    Map<UUID, String> answers = new HashMap<>();
    boolean isAnonymous;
    String pollDescription;
    List<String> tempMultipleAnswers = new ArrayList<>();
    
    public PollSession(TestPoll poll) {
        this.pollId = poll.id;
        this.questions = poll.questions;
        this.isAnonymous = poll.isAnonymous;
        this.pollDescription = poll.description;
    }
    
    public TestQuestion getCurrentQuestion() {
        if (currentQuestionIndex < questions.size()) {
            return questions.get(currentQuestionIndex);
        }
        return null;
    }
    
    public boolean hasNextQuestion() {
        return currentQuestionIndex < questions.size();
    }
    
    public void nextQuestion() {
        currentQuestionIndex++;
        tempMultipleAnswers.clear();
    }
    
    public boolean isComplete() {
        return currentQuestionIndex >= questions.size();
    }
}



public class TestPoll {

    UUID id;
    String description;
    boolean isAnonymous;
    List<TestQuestion> questions = new ArrayList<>();
    
    public TestPoll(UUID id, String description, boolean isAnonymous) {
        this.id=id;
    	this.description = description;
        this.isAnonymous = isAnonymous;
    }

}




/*
 * private void initTestData() { TestPoll poll1 = new
 * TestPoll(UUID.randomUUID(),"Опрос о программировании", true);
 * 
 * TestQuestion q1 = new TestQuestion(QuestionType.MULTIPLE,
 * "Какой язык программирования вы используете чаще всего?");
 * q1.variants.add("Java"); q1.variants.add("Python");
 * q1.variants.add("JavaScript"); q1.variants.add("C++"); q1.variants.add("Go");
 * poll1.questions.add(q1);
 * 
 * TestQuestion q2 = new TestQuestion(QuestionType.OPENED,
 * "Почему вы выбрали именно этот язык?"); poll1.questions.add(q2);
 * 
 * TestQuestion q3 = new TestQuestion(QuestionType.CLOSED,
 * "Сколько лет вы программируете?"); q3.variants.add("Меньше года");
 * q3.variants.add("1-3 года"); q3.variants.add("3-5 лет");
 * q3.variants.add("5-10 лет"); q3.variants.add("Больше 10 лет");
 * poll1.questions.add(q3);
 * 
 * testPolls.add(poll1);
 * 
 * TestPoll poll2 = new TestPoll(UUID.randomUUID(),"Опрос о работе", false);
 * 
 * TestQuestion q4 = new TestQuestion(QuestionType.MULTIPLE,
 * "Какие инструменты вы используете в работе?"); q4.variants.add("Git");
 * q4.variants.add("Docker"); q4.variants.add("Kubernetes");
 * q4.variants.add("Jenkins"); q4.variants.add("Gradle/Maven");
 * q4.variants.add("Jira"); poll2.questions.add(q4);
 * 
 * TestQuestion q5 = new TestQuestion(QuestionType.CLOSED,
 * "Как вы оцениваете свою зарплату?"); q5.variants.add("Ниже рынка");
 * q5.variants.add("На уровне рынка"); q5.variants.add("Выше рынка");
 * q5.variants.add("Значительно выше рынка"); poll2.questions.add(q5);
 * 
 * testPolls.add(poll2);
 * 
 * TestPoll poll3 = new TestPoll(UUID.randomUUID(),"Опрос о предпочтениях",
 * true);
 * 
 * TestQuestion q6 = new TestQuestion(QuestionType.MULTIPLE,
 * "Какой чай вы предпочитаете?"); q6.variants.add("Черный");
 * q6.variants.add("Зеленый"); q6.variants.add("Травяной");
 * q6.variants.add("Фруктовый"); q6.variants.add("Не пью чай");
 * poll3.questions.add(q6);
 * 
 * TestQuestion q7 = new TestQuestion(QuestionType.OPENED,
 * "Какой у вас любимый напиток?"); poll3.questions.add(q7);
 * 
 * testPolls.add(poll3);
 * 
 * TestPoll poll4 = new TestPoll(UUID.randomUUID(),"Опрос о технологиях",
 * false);
 * 
 * TestQuestion q8 = new TestQuestion(QuestionType.MULTIPLE,
 * "Какие базы данных вы использовали?"); q8.variants.add("PostgreSQL");
 * q8.variants.add("MySQL"); q8.variants.add("MongoDB");
 * q8.variants.add("Redis"); q8.variants.add("Elasticsearch");
 * q8.variants.add("Oracle"); poll4.questions.add(q8);
 * 
 * TestQuestion q9 = new TestQuestion(QuestionType.CLOSED,
 * "Какой фреймворк вы предпочитаете?"); q9.variants.add("Spring Boot");
 * q9.variants.add("Django"); q9.variants.add("Express.js");
 * q9.variants.add("ASP.NET"); q9.variants.add("Не использую фреймворки");
 * poll4.questions.add(q9);
 * 
 * testPolls.add(poll4);
 * 
 * System.out.println("✅ Загружено тестовых опросов: " + testPolls.size()); }
 */