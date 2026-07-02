package Bot;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import org.telegram.telegrambots.meta.generics.TelegramClient;


public class Bot implements LongPollingSingleThreadUpdateConsumer {
	private TelegramClient telegramClient = new OkHttpTelegramClient(Constants.botToken);;
	//хранение информации о пользователе
	private Map<Long, String> userState = new HashMap<>();
    private Map<Long, String> userNames = new HashMap<>();
    private Map<Long, String> userTagsGender=new HashMap<>();
    private Map<Long, String> userTagsProfession=new HashMap<>();
    //как будет бд так переедет туда
    public String getBotUsername() {
        return Constants.botName; 
    }
    public String getBotToken() {
        return Constants.botToken;
    }
    @Override
    public void consume(Update update) {
    	if (update.hasMessage() && update.getMessage().hasText()) {
    		 long chatId = update.getMessage().getChatId();
    		String messageText = update.getMessage().getText();
            if (messageText.equals("/start")) {
                startRegistration(chatId);
                return;
            }
            String state = userState.get(chatId);
            switch (state) {
            case("AWAITING_NAME"):
            	processNameInput(chatId, messageText);
            case("AWAITING_TAGS_gender"):
            	processTagsGender(chatId,messageText);
            return;
            case("AWAITING_TAGS_profession"):
            	processTagsProfession(chatId,messageText);
            return;
            default:
                // Регистрация завершена
                sendMessage(chatId, "Вы уже зарегистрированы! Ба бу бэ).");
            }
            }
        if(update.hasCallbackQuery()) {
        	System.out.println("коллбэк есть");
        	Long chatId= update.getCallbackQuery().getMessage().getChatId();
        	String messageText=update.getCallbackQuery().getData();
        	handleCallback(chatId,update);
            String state = userState.get(chatId);
            switch (state) {
            case("AWAITING_NAME"):
            	processNameInput(chatId, messageText);
            return;
            case("AWAITING_TAGS_gender"):
            	processTagsGender(chatId,messageText);
            return;
            case("AWAITING_TAGS_profession"):
            	processTagsProfession(chatId,messageText);
            return;
            }
        }
        
    }

	// Метод начала регистрации
    private void startRegistration(long chatId) {
        // Устанавливаем состояние "ожидание имени"
        userState.put(chatId, "AWAITING_NAME");

        SendMessage message = SendMessage.builder()
                .chatId(chatId)
                .text("Привет, это бот для проведения опросов!\n" +
                      "Напишите свою фамилию и имя одним сообщением, например:\n" +
                      "Иванов Иван")
                .build();

        try {
            telegramClient.execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }
    
    // Метод обработки введённого имени
    private boolean processNameInput(long chatId, String fullName) {
        // Проверяем, что имя состоит минимум из двух слов
        String[] parts = fullName.trim().split("\\s+");
        if (parts.length < 2) {
            sendMessage(chatId, " Пожалуйста, введите фамилию и имя через пробел:\n" +
                               "Например: Иванов Иван");
            return false;
        }
        this.userNames.put(chatId, fullName);
        userState.put(chatId, "AWAITING_TAGS_gender");
        return true;
    }
    private void processTagsGender(long chatId, String messageText) {
    	InlineKeyboardButton male = InlineKeyboardButton.builder()
                .text("Мужчина")
                .callbackData("tag_male")
                .build();
        InlineKeyboardButton female = InlineKeyboardButton.builder()
                .text("Женщина")
                .callbackData("tag_female")
                .build();
        InlineKeyboardRow row = new InlineKeyboardRow();
        row.add(male);
        row.add(female);
        List<InlineKeyboardRow> rows = new ArrayList<>();
        rows.add(row);
        InlineKeyboardMarkup keyboard = InlineKeyboardMarkup.builder()
                .keyboard(rows)
                .build();

        SendMessage message = SendMessage.builder()
                .chatId(chatId)
                .text("Ваш пол:")
                .replyMarkup(keyboard)
                .build();

        try {
            telegramClient.execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }
    
    private void processTagsProfession(long chatId, String messageText) {
    	InlineKeyboardButton prog = InlineKeyboardButton.builder()
                .text("Программист")
                .callbackData("tag_programmer")
                .build();
        InlineKeyboardButton manag = InlineKeyboardButton.builder()
                .text("Менеджер")
                .callbackData("tag_manager")
                .build();
        InlineKeyboardRow row = new InlineKeyboardRow();
        row.add(prog);
        row.add(manag);
        List<InlineKeyboardRow> rows = new ArrayList<>();
        rows.add(row);
        InlineKeyboardMarkup keyboard = InlineKeyboardMarkup.builder()
                .keyboard(rows)
                .build();

        SendMessage message = SendMessage.builder()
                .chatId(chatId)
                .text("Ваша профессия:")
                .replyMarkup(keyboard)
                .build();

        try {
            telegramClient.execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }
    
    private void handleCallback(long chatId,Update update) {
    	String CallData=update.getCallbackQuery().getData();
    	if (CallData.startsWith("tag_")) {
    		switch(CallData) {
    		case("tag_male"):
    		case("tag_female"):
    			userTagsGender.put(chatId,CallData);
            userState.put(chatId, "AWAITING_TAGS_profession");
    		break;
    		case("tag_programmer"):
    		case("tag_manager"):
    		userTagsProfession.put(chatId, CallData);
            userState.put(chatId, "REGISTERED");
    		break;
    		}
    	}
 
    };
    private void sendMessage(long chatId, String text) {
        SendMessage message = SendMessage.builder()
                .chatId(chatId)
                .text(text)
                .build();
        try {
            telegramClient.execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }
}