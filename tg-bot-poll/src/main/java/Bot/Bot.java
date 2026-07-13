package Bot;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageReplyMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import Bot.DBConnection.PollInfo;

enum State {
	AWAITING_FIRST_NAME, AWAITING_LAST_NAME, AWAITING_TAGS, REGISTERED, READY, IN_POLL, AWAITING_POLL_ANSWER,
	AWAITING_HR_PASSWORD,AWAITING_NEW_POLL_NAME,AWAITING_NEW_POLL_QUESTION,AWAITING_NEW_QUESTION_ANSWER
}

public class Bot implements LongPollingSingleThreadUpdateConsumer {
	private TelegramClient telegramClient = new OkHttpTelegramClient(Constants.botToken);

	private Map<Long, State> userState = new HashMap<>();
	private Map<Long, String> userFirstNames = new HashMap<>();
	private Map<Long, String> userFamilyNames = new HashMap<>();
	private Map<Long, ArrayList<String>> userTags = new HashMap<>();
	private Map<Long, PollSession> pollSessions = new HashMap<>();
	private Map<Long, List<TestPoll>> testPolls = new HashMap<>();

	public Bot() {

	}



	public String getBotUsername() {
		return Constants.botName;
	}

	public String getBotToken() {
		return Constants.botToken;
	}

	@Override
	public void consume(Update update) {
		try {
			if (update.hasMessage() && update.getMessage().hasText()) {
				long chatId = update.getMessage().getChatId();
				String messageText = update.getMessage().getText();
				if (messageText.equals("/start")) {
					startRegistration(chatId);
					return;
				}
				State state = userState.get(chatId);
				if (state == null) {
					if (DBConnection.isUserRegistered(chatId)) {
						userState.put(chatId, State.valueOf(DBConnection.getUserState(chatId)));
						userTags.put(chatId, DBConnection.getUserTagCallbacks(chatId));
					} else {
						sendMessage(chatId, "Пожалуйста, начните с команды /start");
						return;
					}
				}
				state = userState.get(chatId);
				switch (state) {
				case AWAITING_FIRST_NAME:
					userTags.put(chatId, new ArrayList<String>());
					processFirstNameInput(chatId, messageText);
					return;
				case AWAITING_LAST_NAME:
					processLastNameInput(chatId, messageText);
					return;
				case REGISTERED:
					sendMenu(chatId);
					return;
				case READY:
					handleMenuCommand(chatId, messageText);
					return;
				case AWAITING_POLL_ANSWER:
					handleFreeTextAnswer(chatId, messageText);
					return;
				case IN_POLL:
					sendMessage(chatId, "Для ответа на вопрос нажмите на кнопку(и) под сообщением.");
				default:
					sendMessage(chatId, "Как ты сюда попал? userState=" + state);
				}
			}
			if (update.hasCallbackQuery()) {
				Long chatId = update.getCallbackQuery().getMessage().getChatId();
				State state = userState.get(chatId);
				if (state == null) {
					if (DBConnection.isUserRegistered(chatId)) {
						userState.put(chatId, State.valueOf(DBConnection.getUserState(chatId)));
						userTags.put(chatId, DBConnection.getUserTagCallbacks(chatId));
					} else {
						sendMessage(chatId, "Пожалуйста, начните с команды /start");
						return;
					}
				}
				state = userState.get(chatId);
				handleCallback(chatId, update);
				return;
			}
		} catch (SQLException e) {
			Long chatId = update.getCallbackQuery().getMessage().getChatId();
			if (chatId == null) {
				chatId = update.getMessage().getChatId();
			}
			if (chatId != null) {
				sendMessage(chatId, "Ошибка базы данных. Попробуйте позже.");
			}
			e.printStackTrace();
		}
	}

	private void startRegistration(long chatId) throws SQLException {
		userState.put(chatId, State.AWAITING_FIRST_NAME);
	    DBConnection.updateUserState(chatId, String.valueOf(State.AWAITING_FIRST_NAME));
		SendMessage message = SendMessage.builder().chatId(chatId)
				.text("Привет, это бот для проведения опросов!\n" + "Напишите своё имя, например:\n" + "Иван").build();
		try {
			telegramClient.execute(message);
		} catch (TelegramApiException e) {
			e.printStackTrace();
		}
	}

	private void processFirstNameInput(long chatId, String firstName) throws SQLException {
		String name = firstName.trim();
		if (name.isEmpty() || name.length() > 50) {
			sendMessage(chatId, "Имя не может быть пустым или слишком длинным.\nВведите имя ещё раз:");
			return;
		}
		if (!name.matches("^[а-яА-ЯёЁa-zA-Z\\-]+$")) {
			sendMessage(chatId, "Имя должно содержать только буквы.\nПопробуйте ещё раз:");
			return;
		}
		this.userFirstNames.put(chatId, name);
		userState.put(chatId, State.AWAITING_LAST_NAME);
	    DBConnection.updateUserState(chatId, String.valueOf(State.AWAITING_LAST_NAME));
		SendMessage message = SendMessage.builder().chatId(chatId)
				.text("Отлично! Теперь введите вашу фамилию, например:\nИванов").build();
		try {
			telegramClient.execute(message);
		} catch (TelegramApiException e) {
			e.printStackTrace();
		}
	}

	private void processLastNameInput(long chatId, String lastName) throws SQLException {
		String name = lastName.trim();
		if (name.isEmpty() || name.length() > 50) {
			sendMessage(chatId, "Фамилия не может быть пустой или слишком длинной.\nВведите фамилию ещё раз:");
			return;
		}
		if (!name.matches("^[а-яА-ЯёЁa-zA-Z\\-]+$")) {
			sendMessage(chatId, "Фамилия должна содержать только буквы.\nПопробуйте ещё раз:");
			return;
		}
		this.userFamilyNames.put(chatId, name);
		processTags(chatId);
		userState.put(chatId, State.AWAITING_TAGS);
	    DBConnection.updateUserState(chatId, String.valueOf(State.AWAITING_TAGS));
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

	private void handleMenuCommand(long chatId, String command) throws SQLException {
		switch (command) {
		case "Мои опросы":
			showAvailablePolls(chatId);
			break;
		case "Статистика":
			sendMessage(chatId, "Статистика пока недоступна");
			sendMenu(chatId);
			break;
		case "Помощь":
			sendMessage(chatId, "Помощь: выберите 'Мои опросы' для просмотра доступных опросов");
			sendMenu(chatId);
			break;
		case "Кто я?":
			String firstName = userFirstNames.getOrDefault(chatId, "Не указано");
			String lastName = userFamilyNames.getOrDefault(chatId, "Не указано");
			ArrayList<String> tags = userTags.getOrDefault(chatId, new ArrayList<>());
			String tagsStr = tags.isEmpty() ? "Нет тегов" : String.join(", ", tags);
			sendMessage(chatId, "👤 Информация о пользователе:\n\n" + "Имя: " + firstName + "\n" + "Фамилия: "
					+ lastName + "\n" + "Теги: " + tagsStr);
			sendMenu(chatId);
			break;
		default:
			sendMessage(chatId, "❌ Неизвестная команда");
			sendMenu(chatId);
		}
	}

	public void sendMenu(long chatId) throws SQLException {
		int uncompletedPolls = getUncompletedPollsCount(chatId);
		userState.put(chatId, State.READY);
	    DBConnection.updateUserState(chatId, String.valueOf(State.READY));
		List<KeyboardRow> rows = new ArrayList<KeyboardRow>();
		KeyboardRow row1 = new KeyboardRow();
		row1.add("Мои опросы");
		row1.add("Статистика");

		rows.add(row1);
		KeyboardRow row2 = new KeyboardRow();
		row2.add("Помощь");
		row2.add("Кто я?");
		rows.add(row2);
		if (userTags.get(chatId).contains("tag_hr")) {
			KeyboardRow row3 = new KeyboardRow();
			row3.add("Создать опрос");
			row3.add("Редактировать опрос");
			rows.add(row3);
			KeyboardRow row4= new KeyboardRow();
			row4.add("Получить результаты по опросу");
			row4.add("Завершить опрос");
			rows.add(row4);
		}

		ReplyKeyboardMarkup keyboard = ReplyKeyboardMarkup.builder().keyboard(List.of(row1, row2)).resizeKeyboard(true)
				.oneTimeKeyboard(false).build();

		SendMessage message;
		if (uncompletedPolls > 0) {
			message = SendMessage.builder().chatId(chatId)
					.text("📋 У вас есть " + uncompletedPolls + " непройденных опросов").replyMarkup(keyboard).build();
		} else {
			message = SendMessage.builder().chatId(chatId).text("✅ У вас пройдены все опросы!").replyMarkup(keyboard)
					.build();
		}
		try {
			telegramClient.execute(message);
		} catch (TelegramApiException e) {
			e.printStackTrace();
		}
	}

	private int getUncompletedPollsCount(long chatId) throws SQLException {
		List<PollInfo> thelist = DBConnection.getAvailablePolls(chatId);
		boolean isAnonymous = true; // TODO: ЭТО ЗАГЛУШКА, ДОЛЖНО БЫТЬ ПО ДРУГОМУ
		testPolls.put(chatId,new ArrayList<TestPoll>() );
		for (PollInfo poll : thelist) {
			testPolls.get(chatId).add(new TestPoll(poll.id, poll.description, isAnonymous));
		}
		return thelist.size();
	}

	private void showAvailablePolls(long chatId) throws SQLException {
	    List<DBConnection.PollInfo> polls;
	    try {
	        polls = DBConnection.getAvailablePolls(chatId);
	    } catch (SQLException e) {
	        e.printStackTrace();
	        sendMessage(chatId, "❌ Ошибка загрузки опросов");
	        sendMenu(chatId);
	        return;
	    }
	    
	    if (polls.isEmpty()) {
	        sendMessage(chatId, "📭 Нет доступных опросов");
	        sendMenu(chatId);
	        return;
	    }
	    
	    List<InlineKeyboardRow> rows = new ArrayList<>();
	    
	    for (DBConnection.PollInfo poll : polls) {
	        InlineKeyboardButton button = InlineKeyboardButton.builder()
	                .text("📝 " + poll.description + " (" + poll.questionCount + " вопросов)")
	                .callbackData("poll_" + poll.id.toString())  // ← UUID вместо индекса
	                .build();
	        InlineKeyboardRow row = new InlineKeyboardRow();
	        row.add(button);
	        rows.add(row);
	        System.out.println("poll_" + poll.id.toString());
	    }
	    
	    InlineKeyboardButton backButton = InlineKeyboardButton.builder()
	            .text("⬅️ Назад в меню")
	            .callbackData("back_to_menu")
	            .build();
	    InlineKeyboardRow backRow = new InlineKeyboardRow();
	    backRow.add(backButton);
	    rows.add(backRow);
	    
	    InlineKeyboardMarkup keyboard = InlineKeyboardMarkup.builder().keyboard(rows).build();
	    
	    SendMessage message = SendMessage.builder()
	            .chatId(chatId)
	            .text("📋 Доступные опросы:\nВыберите опрос для прохождения")
	            .replyMarkup(keyboard)
	            .build();
	    
	    try {
	        telegramClient.execute(message);
	    } catch (TelegramApiException e) {
	        e.printStackTrace();
	    }
	}

	private void startPoll(long chatId, UUID pollId) throws SQLException {
	    TestPoll selectedPoll;
	    try {
	        selectedPoll = DBConnection.getPollFromDB(pollId);
	    } catch (SQLException e) {
	        e.printStackTrace();
	        sendMessage(chatId, "❌ Ошибка загрузки опроса");
	        sendMenu(chatId);
	        return;
	    }
	    
	    if (selectedPoll == null) {
	        sendMessage(chatId, "❌ Опрос не найден");
	        sendMenu(chatId);
	        return;
	    }
	    
	    if (selectedPoll.questions.isEmpty()) {
	        sendMessage(chatId, "❌ В опросе нет вопросов");
	        sendMenu(chatId);
	        return;
	    }
	    
	    PollSession session = new PollSession(selectedPoll);
	    pollSessions.put(chatId, session);
	    userState.put(chatId, State.IN_POLL);
	    DBConnection.updateUserState(chatId, String.valueOf(State.IN_POLL));
	    sendMessage(chatId, "📋 Начинаем опрос: " + selectedPoll.description 
	            + "\n\nВсего вопросов: " + selectedPoll.questions.size());
	    showNextQuestion(chatId);
	}
	private void showNextQuestion(long chatId) throws SQLException {
		PollSession session = pollSessions.get(chatId);
		if (session == null) {
			sendMessage(chatId, "❌ Сессия опроса не найдена");
			sendMenu(chatId);
			return;
		}

		TestQuestion question = session.getCurrentQuestion();
		if (question == null) {
			finishPoll(chatId);
			return;
		}

		int questionNumber = session.currentQuestionIndex + 1;
		int totalQuestions = session.questions.size();

		String header = "📝 **Вопрос " + questionNumber + "/" + totalQuestions + "**\n\n";

		switch (question.type) {
		case OPENED:
			sendMessage(chatId, header + question.description + "\n\n✏️ Напишите ваш ответ:");
			userState.put(chatId, State.AWAITING_POLL_ANSWER);
		    DBConnection.updateUserState(chatId, String.valueOf(State.AWAITING_POLL_ANSWER));
			break;
		case CLOSED:
			sendQuestionWithButtons(chatId, question, header, false);
			break;
		case MULTIPLE:
			sendQuestionWithButtons(chatId, question, header, true);
			break;
		}
	}

	private void sendQuestionWithButtons(long chatId, TestQuestion question, String header, boolean allowMultiple) {
		PollSession session = pollSessions.get(chatId);
		InlineKeyboardMarkup keyboard = buildQuestionKeyboard(question, session, allowMultiple);

		String text = header + question.description;
		if (allowMultiple) {
			text += "\n\n(Можно выбрать несколько вариантов, затем нажмите 'Готово')";
		}

		SendMessage message = SendMessage.builder().chatId(chatId).text(text).replyMarkup(keyboard).build();

		try {
			telegramClient.execute(message);
		} catch (TelegramApiException e) {
			e.printStackTrace();
		}
	}

	// Метод для построения клавиатуры вопроса с использованием индексов
	private InlineKeyboardMarkup buildQuestionKeyboard(TestQuestion question, PollSession session,
			boolean allowMultiple) {
		List<InlineKeyboardRow> rows = new ArrayList<>();
		int questionIndex = session.currentQuestionIndex;

		for (int i = 0; i < question.variants.size(); i++) {
			String variant = question.variants.get(i);
			String callbackData;
			String buttonText;

			if (allowMultiple) {
				boolean isSelected = session.tempMultipleAnswers.contains(variant);
				buttonText = (isSelected ? "✅ " : "⬜ ") + variant;
				// Формат: m_<questionIndex>_<variantIndex>
				callbackData = "m_" + questionIndex + "_" + i;
			} else {
				buttonText = variant;
				// Формат: s_<questionIndex>_<variantIndex>
				callbackData = "s_" + questionIndex + "_" + i;
			}

			InlineKeyboardButton button = InlineKeyboardButton.builder().text(buttonText).callbackData(callbackData)
					.build();
			InlineKeyboardRow row = new InlineKeyboardRow();
			row.add(button);
			rows.add(row);
		}

		if (allowMultiple) {
			InlineKeyboardButton doneButton = InlineKeyboardButton.builder().text("✅ Готово")
					.callbackData("md_" + session.currentQuestionIndex) // md = multiple done
					.build();
			InlineKeyboardRow doneRow = new InlineKeyboardRow();
			doneRow.add(doneButton);
			rows.add(doneRow);
		}

		return InlineKeyboardMarkup.builder().keyboard(rows).build();
	}

	private void handleFreeTextAnswer(long chatId, String answer) throws SQLException {
		PollSession session = pollSessions.get(chatId);
		if (session == null) {
			sendMessage(chatId, "❌ Сессия опроса не найдена");
			sendMenu(chatId);
			return;
		}

		TestQuestion question = session.getCurrentQuestion();
		if (question != null) {
			session.answers.put(question.id, answer);
			session.nextQuestion();

			if (session.hasNextQuestion()) {
				showNextQuestion(chatId);
			} else {
				finishPoll(chatId);
			}
		}
	}

	private void finishPoll(long chatId) throws SQLException {
		PollSession session = pollSessions.get(chatId);
		if (session == null)
			return;

		StringBuilder result = new StringBuilder();
		result.append("🎉 Опрос завершен!\n\n");
		result.append("📋 Ваши ответы:\n\n");

		for (TestQuestion question : session.questions) {
			String answer = session.answers.getOrDefault(question.id, "❌ Не отвечено");
			result.append("❓ ").append(question.description).append("\n");
			result.append("   ➡️ ").append(answer).append("\n\n");
		}

		pollSessions.remove(chatId);
		userState.put(chatId, State.READY);
	    DBConnection.updateUserState(chatId, String.valueOf(State.READY));

		sendMessage(chatId, result.toString());
		sendMenu(chatId);
	}

	private void handleCallback(long chatId, Update update) throws SQLException {
		int messageId = update.getCallbackQuery().getMessage().getMessageId();
		String callData = update.getCallbackQuery().getData();
		if(callData.equals("return_from_hr_password")) {
			DeleteMessage deleteMessage = DeleteMessage.builder().chatId(chatId).messageId(messageId).build();
			try {
				telegramClient.execute(deleteMessage);
			} catch (TelegramApiException e) {
				e.printStackTrace();
			}
			processTags(chatId);
			userState.put(chatId, State.AWAITING_TAGS);
		    DBConnection.updateUserState(chatId, String.valueOf(State.AWAITING_TAGS));
		}
		// Назад в меню
		if (callData.equals("back_to_menu")) {
			// чистка опросов для этого пользователя
			testPolls.get(chatId).removeAll(testPolls.get(chatId));
			DeleteMessage deleteMessage = DeleteMessage.builder().chatId(chatId).messageId(messageId).build();
			try {
				telegramClient.execute(deleteMessage);
			} catch (TelegramApiException e) {
				e.printStackTrace();
			}
			sendMenu(chatId);
			return;
		}

		// Запуск опроса (формат: poll_<UUID>)
		if (callData.startsWith("poll_")) {
		    try {
		        String uuidStr = callData.substring("poll_".length());
		        UUID pollId = UUID.fromString(uuidStr);  // ← парсим UUID вместо int
		        
		        DeleteMessage deleteMessage = DeleteMessage.builder()
		                .chatId(chatId)
		                .messageId(messageId)
		                .build();
		        try {
		            telegramClient.execute(deleteMessage);
		        } catch (TelegramApiException e) {
		            e.printStackTrace();
		        }
		        
		        startPoll(chatId, pollId);  // ← передаём UUID
		    } catch (IllegalArgumentException e) {
		        // IllegalArgumentException бросается, если строка не является валидным UUID
		        sendMessage(chatId, "❌ Ошибка: неверный формат опроса");
		    }
		    return;
		}

		// Одиночный выбор (формат: s_<questionIndex>_<variantIndex>)
		if (callData.startsWith("s_")) {
			String[] parts = callData.split("_");
			if (parts.length == 3) {
				try {
					int questionIndex = Integer.parseInt(parts[1]);
					int variantIndex = Integer.parseInt(parts[2]);

					PollSession session = pollSessions.get(chatId);
					if (session != null && questionIndex == session.currentQuestionIndex) {
						TestQuestion question = session.getCurrentQuestion();
						if (question != null && variantIndex >= 0 && variantIndex < question.variants.size()) {
							String variantText = question.variants.get(variantIndex);
							session.answers.put(question.id, variantText);
							session.nextQuestion();

							DeleteMessage deleteMessage = DeleteMessage.builder().chatId(chatId).messageId(messageId)
									.build();
							try {
								telegramClient.execute(deleteMessage);
							} catch (TelegramApiException e) {
								e.printStackTrace();
							}

							if (session.hasNextQuestion()) {
								showNextQuestion(chatId);
							} else {
								finishPoll(chatId);
							}
						}
					}
				} catch (NumberFormatException e) {
					e.printStackTrace();
				}
			}
			return;
		}

		// Множественный выбор - выбор варианта (формат:
		// m_<questionIndex>_<variantIndex>)
		if (callData.startsWith("m_")) {
			String[] parts = callData.split("_");
			if (parts.length == 3) {
				try {
					int questionIndex = Integer.parseInt(parts[1]);
					int variantIndex = Integer.parseInt(parts[2]);

					PollSession session = pollSessions.get(chatId);
					if (session != null && questionIndex == session.currentQuestionIndex) {
						TestQuestion question = session.getCurrentQuestion();
						if (question != null && variantIndex >= 0 && variantIndex < question.variants.size()) {
							String variantText = question.variants.get(variantIndex);

							if (session.tempMultipleAnswers.contains(variantText)) {
								session.tempMultipleAnswers.remove(variantText);
							} else {
								session.tempMultipleAnswers.add(variantText);
							}

							EditMessageReplyMarkup editKeyboard = EditMessageReplyMarkup.builder().chatId(chatId)
									.messageId(messageId).replyMarkup(buildQuestionKeyboard(question, session, true))
									.build();
							try {
								telegramClient.execute(editKeyboard);
							} catch (TelegramApiException e) {
								e.printStackTrace();
							}
						}
					}
				} catch (NumberFormatException e) {
					e.printStackTrace();
				}
			}
			return;
		}

		// Множественный выбор - кнопка "Готово" (формат: md_<questionIndex>)
		if (callData.startsWith("md_")) {
			try {
				int questionIndex = Integer.parseInt(callData.substring("md_".length()));

				PollSession session = pollSessions.get(chatId);
				if (session != null && questionIndex == session.currentQuestionIndex) {
					TestQuestion question = session.getCurrentQuestion();
					if (question != null) {
						if (session.tempMultipleAnswers.isEmpty()) {
							sendMessage(chatId, "⚠️ Выберите хотя бы один вариант!");
							return;
						}

						String answer = String.join(", ", session.tempMultipleAnswers);
						session.answers.put(question.id, answer);
						session.nextQuestion();

						DeleteMessage deleteMessage = DeleteMessage.builder().chatId(chatId).messageId(messageId)
								.build();
						try {
							telegramClient.execute(deleteMessage);
						} catch (TelegramApiException e) {
							e.printStackTrace();
						}

						if (session.hasNextQuestion()) {
							showNextQuestion(chatId);
						} else {
							finishPoll(chatId);
						}
					}
				}
			} catch (NumberFormatException e) {
				e.printStackTrace();
			}
			return;
		}

		// ОБРАБОТКА ТЕГА (toggle: добавить или убрать)
		if (callData.startsWith("tag_")) {
			String tagKey = callData;
			ArrayList<String> tags = userTags.get(chatId);
			if (tags == null) {
				tags = new ArrayList<>();
				userTags.put(chatId, tags);
			}

			// Переключаем: если тег уже выбран - убираем, если нет - добавляем
			if (tags.contains(tagKey)) {
				tags.remove(tagKey);
			} else {
				tags.add(tagKey);
			}

			EditMessageReplyMarkup editKeyboard = EditMessageReplyMarkup.builder().chatId(chatId).messageId(messageId)
					.replyMarkup(getTagReplyKeyboard(chatId)).build();
			try {
				telegramClient.execute(editKeyboard);
			} catch (TelegramApiException e) {
				e.printStackTrace();
			}
			return;
		}
		
		// ПОДТВЕРЖДЕНИЕ ТЕГОВ
		if (callData.equals("accept_tags")) {
			ArrayList<String> tags = userTags.get(chatId);
			if (tags == null || tags.isEmpty()) {
				sendMessage(chatId, "⚠️ Выберите хотя бы один тег!");
				return;
			}

			DeleteMessage deleteMessage = DeleteMessage.builder().chatId(chatId).messageId(messageId).build();
			try {
				telegramClient.execute(deleteMessage);
			} catch (TelegramApiException e) {
				e.printStackTrace();
			}
			if(tags.contains("tag_hr")) {
				userState.put(chatId, State.AWAITING_HR_PASSWORD);
			    DBConnection.updateUserState(chatId, String.valueOf(State.AWAITING_HR_PASSWORD));
				InlineKeyboardButton button=InlineKeyboardButton.builder().text("Вернуться")
						.callbackData("return_from_hr_password").build();
				InlineKeyboardRow row=new InlineKeyboardRow(button);
				InlineKeyboardMarkup back= InlineKeyboardMarkup.builder().keyboardRow(row).build();
				SendMessage message = SendMessage.builder().chatId(chatId).text("Введите пароль для доступа к функциям HR.")
						.replyMarkup(back)
						.build();
				try {
					telegramClient.execute(message);
				} catch (TelegramApiException e) {
					e.printStackTrace();
				}
				return;
			}
			
			userState.put(chatId, State.REGISTERED);
		    DBConnection.updateUserState(chatId, String.valueOf(State.REGISTERED));			sendMessage(chatId, "✅ Регистрация завершена! Вы выбрали теги: " + String.join(", ", tags));
			DBConnection.sendRegisteredUser(chatId, userFirstNames.get(chatId), userFamilyNames.get(chatId),
					userTags.get(chatId));

			sendMenu(chatId);
		}
	}

	private void sendMessage(long chatId, String text) {
		SendMessage message = SendMessage.builder().chatId(chatId).text(text).build();
		try {
			telegramClient.execute(message);
		} catch (TelegramApiException e) {
			e.printStackTrace();
		}
	}

	private InlineKeyboardMarkup getTagReplyKeyboard(Long chatId) {
		ArrayList<String> tags = userTags.get(chatId);
		if (tags == null) {
			tags = new ArrayList<>();
			userTags.put(chatId, tags);
		}

		List<InlineKeyboardRow> rows = new ArrayList<>();
		try {
			List<DBConnection.TagFamilyInfo> families = DBConnection.getAllTagFamilies();

			for (DBConnection.TagFamilyInfo family : families) {
				// Все теги этого семейства в одной строке
				InlineKeyboardRow tagRow = new InlineKeyboardRow();
				for (DBConnection.TagInfo tag : family.tags) {
					boolean isSelected = tags.contains(tag.callbackData);
					String buttonText = (isSelected ? "✅ " : "⬜ ") + tag.name;

					// Все кнопки используют один формат: tag_<something>
					InlineKeyboardButton btn = InlineKeyboardButton.builder().text(buttonText)
							.callbackData(tag.callbackData).build();
					tagRow.add(btn);
				}
				rows.add(tagRow);
			}
		} catch (SQLException e) {
			e.printStackTrace();
			sendMessage(chatId, "Ошибка загрузки тегов из БД");
		}

		// Кнопка "Готово"
		InlineKeyboardButton accept = InlineKeyboardButton.builder().text("✅ Готово").callbackData("accept_tags")
				.build();
		InlineKeyboardRow acceptRow = new InlineKeyboardRow();
		acceptRow.add(accept);
		rows.add(acceptRow);

		return InlineKeyboardMarkup.builder().keyboard(rows).build();
	}
}