package Bot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.telegram.telegrambots.meta.api.methods.polls.SendPoll;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageReplyMarkup;
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

import com.vdurmont.emoji.EmojiParser;

public class Bot implements LongPollingSingleThreadUpdateConsumer {
	private TelegramClient telegramClient = new OkHttpTelegramClient(Constants.botToken);;
	// хранение информации о пользователе
	private Map<Long, String> userState = new HashMap<>();
	private Map<Long, String> userNames = new HashMap<>();
	private Map<Long, ArrayList<String>> userTags = new HashMap<>();
	// TODO: переносить данные в базу данных после регистрации

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
			case ("AWAITING_NAME"):
				userTags.put(chatId, new ArrayList<String>());
				processNameInput(chatId, messageText);
				return;
			case ("REGISTERED"):
				sendMenu(chatId);
			case ("READY"):
				switch (messageText) {
				case ("Кто я?"):
					sendMenu(chatId);
					break;
				case ("Мои опросы"):
					sendMessage(chatId, "Пока хз");
					sendMenu(chatId);
					break;
				case ("Статистика"):
					sendMessage(chatId, "Пока хз");
					sendMenu(chatId);
					break;
				case ("Помощь"):
					sendMessage(chatId, "Пока хз");
					sendMenu(chatId);
					break;
				}
				break;
			case ("FREE_TEXT_ANSWERING"):
				// обработка ответа
				break;
			default:
				sendMessage(chatId, "Как ты сюда попал?");
			}
		}
		if (update.hasCallbackQuery()) {
			Long chatId = update.getCallbackQuery().getMessage().getChatId();
			String messageText = update.getCallbackQuery().getData();
			handleCallback(chatId, update);
			String state = userState.get(chatId);
			switch (state) {
			case ("AWAITING_NAME"):
				processNameInput(chatId, messageText);
			case ("AWAITING_TAGS"):
				return;
			case ("REGISTERED"):
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

		SendMessage message = SendMessage.builder().chatId(chatId).text("Привет, это бот для проведения опросов!\n"
				+ "Напишите свою фамилию и имя одним сообщением, например:\n" + "Иванов Иван").build();

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
			sendMessage(chatId, " Пожалуйста, введите фамилию и имя через пробел:\n" + "Например: Иванов Иван");
			return false;
		}
		this.userNames.put(chatId, fullName);
		processTags(chatId);
		userState.put(chatId, "AWAITING_TAGS");
		return true;
	}

	private void processTags(long chatId) {
		InlineKeyboardMarkup keyboard = getTagReplyKeyboard(chatId);

		SendMessage message = SendMessage.builder().chatId(chatId).text("Выберите теги, которые вам соответствуют:")
				.replyMarkup(keyboard).build();
		try {
			telegramClient.execute(message);
		} catch (TelegramApiException e) {
			e.printStackTrace();
		}
	}

	public void sendMenu(long chatId) {
		int uncompletedPolls = 0; // TODO: сделать счетчик обновляемым с базы данных
		userState.put(chatId, "READY");
		KeyboardRow row1 = new KeyboardRow();
		row1.add("Мои опросы");
		row1.add("Статистика");
		KeyboardRow row2 = new KeyboardRow();
		row2.add(" Помощь");
		row2.add("Кто я?");
		ReplyKeyboardMarkup keyboard = ReplyKeyboardMarkup.builder().keyboard(List.of(row1, row2)).resizeKeyboard(true)
				.oneTimeKeyboard(false).build();
		SendMessage message = null;
		if (uncompletedPolls > 0) {
			message = SendMessage.builder().chatId(chatId)
					.text("У вас есть непройденных " + uncompletedPolls + " тестов").replyMarkup(keyboard).build();
		} else {
			message = SendMessage.builder().chatId(chatId).text("У вас пройдены все тесты!").replyMarkup(keyboard)
					.build();
		}
		try {
			telegramClient.execute(message);
		} catch (TelegramApiException e) {
			e.printStackTrace();
		}
	}

	private void handleCallback(long chatId, Update update) {
		int messageId = update.getCallbackQuery().getMessage().getMessageId();
		String CallData = update.getCallbackQuery().getData();
		if (CallData.startsWith("refuse_tag_")) {
			userTags.get(chatId).remove(CallData.substring(7));
			EditMessageReplyMarkup editKeyboard = EditMessageReplyMarkup.builder().chatId(chatId).messageId(messageId)
					.replyMarkup(getTagReplyKeyboard(chatId)).build();
			try {
				telegramClient.execute(editKeyboard);
			} catch (TelegramApiException e) {
				e.printStackTrace();
			}
		} else if (CallData.startsWith("tag_")) {
			userTags.get(chatId).add(CallData);
			EditMessageReplyMarkup editKeyboard = EditMessageReplyMarkup.builder().chatId(chatId).messageId(messageId)
					.replyMarkup(getTagReplyKeyboard(chatId)).build();
			try {
				telegramClient.execute(editKeyboard);
			} catch (TelegramApiException e) {
				e.printStackTrace();
			}
		} else if (CallData.startsWith("accept_tags")) {
			// добавление в бд
		    DeleteMessage deleteMessage = DeleteMessage.builder()
		            .chatId(chatId)
		            .messageId(messageId)
		            .build();
		    try {
		        telegramClient.execute(deleteMessage);
		    } catch (TelegramApiException e) {
		        e.printStackTrace();
		    }
			userState.put(chatId, "REGISTERED");
		}
	};

	private void sendMessage(long chatId, String text) {
		SendMessage message = SendMessage.builder().chatId(chatId).text(text).build();
		try {
			telegramClient.execute(message);
		} catch (TelegramApiException e) {
			e.printStackTrace();
		}
	}

	private InlineKeyboardMarkup getTagReplyKeyboard(Long chatId) {
		// TODO: выглядит плохо, надо будет переделать чтобы работало с бд
		InlineKeyboardMarkup keyboard = null;
		String checkmarkEmoji = EmojiParser.parseToUnicode(":white_check_mark:");
		InlineKeyboardButton prog, manag, male, female = null;
		if (userTags.get(chatId).contains("tag_programmer")) {
			prog = InlineKeyboardButton.builder().text(checkmarkEmoji + "Программист")
					.callbackData("refuse_tag_programmer").build();
		} else {
			prog = InlineKeyboardButton.builder().text("Программист").callbackData("tag_programmer").build();
		}
		if (userTags.get(chatId).contains("tag_manager")) {
			manag = InlineKeyboardButton.builder().text(checkmarkEmoji + "Менеджер").callbackData("refuse_tag_manager")
					.build();
		} else {
			manag = InlineKeyboardButton.builder().text("Менеджер").callbackData("tag_manager").build();
		}
		if (userTags.get(chatId).contains("tag_male")) {
			male = InlineKeyboardButton.builder().text(checkmarkEmoji + "Мужчина").callbackData("refuse_tag_male")
					.build();
		} else {
			male = InlineKeyboardButton.builder().text("Мужчина").callbackData("tag_male").build();
		}
		if (userTags.get(chatId).contains("tag_female")) {
			female = InlineKeyboardButton.builder().text(checkmarkEmoji + "Женщина").callbackData("refuse_tag_female")
					.build();
		} else {
			female = InlineKeyboardButton.builder().text("Женщина").callbackData("tag_female").build();
		}
		InlineKeyboardButton accept = InlineKeyboardButton.builder().text(checkmarkEmoji).callbackData("accept_tags")
				.build();

		InlineKeyboardRow row1 = new InlineKeyboardRow();
		InlineKeyboardRow row2 = new InlineKeyboardRow();
		InlineKeyboardRow row3 = new InlineKeyboardRow();
		row1.add(male);
		row1.add(female);
		row2.add(manag);
		row2.add(prog);
		row3.add(accept);
		List<InlineKeyboardRow> rows = new ArrayList<>();
		rows.add(row1);
		rows.add(row2);
		rows.add(row3);
		keyboard = InlineKeyboardMarkup.builder().keyboard(rows).build();
		return keyboard;
	}

	// TODO: Неготовый конструктор отправителя опросов
	private void PollQuestionConstructor(long chatId, String description, boolean isAnonymous, boolean hasFreeText,
			boolean hasMultipleAnswers, ArrayList<String> options) {
		if (hasFreeText) {
			sendMessage(chatId, description);
			userState.put(chatId, "FREE_TEXT_ANSWERING");
		} else {
			SendPoll sendQuestion = SendPoll.builder().allowMultipleAnswers(hasMultipleAnswers).build();
		}
	}
}