import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;

import java.io.IOException;
import java.time.Duration;

/**
 *
 * Asailify, a bot created at 2 am. Its founding purpose is to find users who disrupt the
 * the discord server. Specifically those who attempt to spam DM advertise.
 * The name is a word play on Assail. To Assail means to Attack or Assault.
 * Though the bots does the opposite and diverts an attack(s) and or assault(s).
 *
 *
 * Main class for bot.
 *
 * @author Gvistic
 * @version 1.0.0 28 May 2021
 *
 */

public class Main { ;
    public static void main(String[] args) {
        DiscordBot discordBot = new DiscordBot();
        RetryPolicy<Object> retryPolicy = new RetryPolicy<>()
                .handle(IOException.class)
                .withDelay(Duration.ofSeconds(1))
                .withMaxRetries(3);
        Failsafe.with(retryPolicy).run(discordBot::startBot);
    }
}
