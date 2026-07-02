package Bot;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.telegram.telegrambots.meta.api.methods.polls.SendPoll;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.generics.TelegramClient;


public class Bot implements LongPollingSingleThreadUpdateConsumer {
	private TelegramClient telegramClient = new OkHttpTelegramClient(Constants.botToken);;
	//хранение информации о пользователе
	private Map<Long, String> userState = new HashMap<>();
    private Map<Long, String> userNames = new HashMap<>();
    private Map<Long, String> userTagsGender=new HashMap<>();
    private Map<Long, String> userTagsProfession=new HashMap<>();
    //TODO: переносить данные в базу данных после регистрации
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
            	processTagsGender(chatId);
            return;
            case("AWAITING_TAGS_profession"):
            	processTagsProfession(chatId);
            return;
            case("REGISTERED"):
            	sendMenu(chatId);
            case("READY"):
            	switch(messageText) {
            	case("Кто я?"):
            		sendMessage(chatId,"Пока хз");
            		sendMenu(chatId);
            		break;
            	case("Мои опросы"):
            		sendMessage(chatId,"Пока хз");
            		sendMenu(chatId);
            		break;
            	case("Статистика"):
            		sendMessage(chatId,"Пока хз");
            		sendMenu(chatId);
            		break;
            	case("Помощь"):
            		sendMessage(chatId,"Пока хз");
            		sendMenu(chatId);
            		break;            	     	
            	}
            break;
            default:
                sendMessage(chatId, "Как ты сюда попал?");
            }
            }
        if(update.hasCallbackQuery()) {
        	Long chatId= update.getCallbackQuery().getMessage().getChatId();
        	String messageText=update.getCallbackQuery().getData();
        	handleCallback(chatId,update);
            String state = userState.get(chatId);
            switch (state) {
            case("AWAITING_NAME"):
            	processNameInput(chatId, messageText);
            case("AWAITING_TAGS_gender"):
            	processTagsGender(chatId);
            return;
            case("AWAITING_TAGS_profession"):
            	processTagsProfession(chatId);
            return;
            case("REGISTERED"):
            	sendMenu(chatId);
            return;
            default:
                sendMessage(chatId, "Ты не должен был сюда попасть");
            
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
    
    
    private void processTagsGender(long chatId) {
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
    
    private void processTagsProfession(long chatId) {
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
    
    public void sendMenu(long chatId) {
    	    int uncompletedPolls=0;                          // TODO: сделать счетчик обновляемым с базы данных
    	    userState.put(chatId,"READY");
    	    KeyboardRow row1 = new KeyboardRow();
    	    row1.add("Мои опросы");
    	    row1.add("Статистика");
    	    KeyboardRow row2 = new KeyboardRow();
    	    row2.add(" Помощь");
    	    row2.add("Кто я?");
    	    ReplyKeyboardMarkup keyboard = ReplyKeyboardMarkup
    	            .builder()
    	            .keyboard(List.of(row1, row2)) 
    	            .resizeKeyboard(true)       
    	            .oneTimeKeyboard(false) 
    	            .build();
    	    SendMessage message=null;
    	    if (uncompletedPolls>0) {
    	     message= SendMessage
    	            .builder()
    	            .chatId(chatId)
    	            .text("У вас есть непройденных "+ uncompletedPolls+" тестов")
    	            .replyMarkup(keyboard)
    	            .build();
    	    }
    	    else {
        	message = SendMessage
        	            .builder()
        	            .chatId(chatId)
        	            .text("У вас пройдены все тесты!")
        	            .replyMarkup(keyboard)
        	            .build();
    	    }
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
    
    //TODO: Неготовый конструктор отправителя опросов
    private void PollConstructor(long chatId,boolean isAnonymous,boolean hasFreeText,boolean hasMultipleAnswers,ArrayList<String>options,int expirationDate) {
    	if(hasFreeText) {
    		 InlineKeyboardButton freeText = InlineKeyboardButton.builder()
    	                .text("Открытый ответ")
    	                .callbackData("answer_FreeText")
    	                .build();
    		InlineKeyboardRow row=new InlineKeyboardRow(freeText);
    		List<InlineKeyboardRow> rows = new ArrayList<>();
            rows.add(row);
            InlineKeyboardMarkup keyboard = InlineKeyboardMarkup.builder()
                    .keyboard(rows)
                    .build();
        	SendPoll sendpoll=SendPoll.builder().allowMultipleAnswers(hasMultipleAnswers).closeDate(expirationDate).replyMarkup(keyboard).build();
    	}
    	SendPoll sendpoll=SendPoll.builder().allowMultipleAnswers(hasMultipleAnswers).closeDate(expirationDate).build();
    }
}