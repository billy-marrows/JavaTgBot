package Bot;
import org.telegram.telegrambots.longpolling.TelegramBotsLongPollingApplication;
public class Main {
    public static void main(String[] args) {
        try(TelegramBotsLongPollingApplication botsApplication = new TelegramBotsLongPollingApplication()){
        botsApplication.registerBot(Constants.botToken, new Bot());
        System.out.println("Бот успешно запущен!");
        Thread.currentThread().join();
        }catch (Exception e) {
        	e.printStackTrace();
        }
    }
}