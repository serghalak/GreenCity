package greencity.config;

import greencity.entity.FactTranslation;
import greencity.entity.User;
import greencity.message.SendHabitNotification;
import greencity.repository.FactTranslationRepo;
import greencity.repository.HabitRepo;
import greencity.repository.UserRepo;
import lombok.AllArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import static greencity.constant.CacheConstants.FACT_OF_THE_DAY_CACHE_NAME;
import static greencity.constant.CacheConstants.HABIT_FACT_OF_DAY_CACHE;
import static greencity.constant.RabbitConstants.EMAIL_TOPIC_EXCHANGE_NAME;
import static greencity.constant.RabbitConstants.SEND_HABIT_NOTIFICATION_ROUTING_KEY;
import static greencity.entity.enums.EmailNotification.*;
import static greencity.entity.enums.FactOfDayStatus.*;


/**
 * Config for scheduling.
 *
 * @author Nazar Stasyuk
 * @version 1.0
 */
@Configuration
@EnableScheduling
@EnableCaching
@AllArgsConstructor
public class ScheduleConfig {
    private final FactTranslationRepo factTranslationRepo;
    private final HabitRepo habitRepo;
    private final RabbitTemplate rabbitTemplate;
    private final UserRepo userRepo;

    /**
     * Invoke {@link sendHabitNotification} from EmailMessageReceiver to send email letters
     * to each user that hasn't marked any habit during last 3 days.
     *
     * @param users list of potential {@link User} to send notifications.
     */
    private void sendHabitNotificationIfNeed(List<User> users) {
        ZonedDateTime end = ZonedDateTime.now();
        ZonedDateTime start = end.minusDays(3);
        for (User user : users) {
            int count = habitRepo.countMarkedHabitsByUserIdByPeriod(user.getId(), start, end);
            if (count == 0) {
                rabbitTemplate.convertAndSend(
                    EMAIL_TOPIC_EXCHANGE_NAME,
                    SEND_HABIT_NOTIFICATION_ROUTING_KEY,
                    new SendHabitNotification(user.getName(), user.getEmail())
                );
            }
        }
    }

    /**
     * Every day at 19:00 sends notifications about not marked habits to users with field
     * {@link greencity.entity.enums.EmailNotification} equal to IMMEDIATELY or DAILY.
     */
    @Scheduled(cron = "0 0 19 * * *")
    void sendHabitNotificationEveryDay() {
        List<User> users = userRepo.findAllByEmailNotification(IMMEDIATELY);
        users.addAll(userRepo.findAllByEmailNotification(DAILY));
        sendHabitNotificationIfNeed(users);
    }

    /**
     * Every friday at 19:00 sends notifications about not marked habits to users with field
     * {@link greencity.entity.enums.EmailNotification} equal to WEEKLY.
     */
    @Scheduled(cron = "0 0 19 * * FRI")
    void sendHabitNotificationEveryWeek() {
        List<User> users = userRepo.findAllByEmailNotification(WEEKLY);
        sendHabitNotificationIfNeed(users);
    }

    /**
     * On th 25th of every month at 19:00 sends notifications about not marked habits to users with field
     * {@link greencity.entity.enums.EmailNotification} equal to MONTHLY.
     */
    @Scheduled(cron = "0 0 19 25 * *")
    void sendHabitNotificationEveryMonth() {
        List<User> users = userRepo.findAllByEmailNotification(MONTHLY);
        sendHabitNotificationIfNeed(users);
    }

    /**
     * Once a day randomly chooses new fact of day that has not been fact of day during this iteration.
     * factOfDay == 0 - wasn't fact of day, 1 - is today's fact of day, 2 - already was fact of day.
     */
    @CacheEvict(value = HABIT_FACT_OF_DAY_CACHE, allEntries = true)
    @Transactional
    @Scheduled(cron = "0 0 0 * * *")
    public void chooseNewFactOfDay() {
        Optional<List<FactTranslation>> list = factTranslationRepo.findRandomFact();
        if (list.isPresent()) {
            factTranslationRepo.updateFactOfDayStatus(CURRENT, USED);
        } else {
            factTranslationRepo.updateFactOfDayStatus(USED, POTENTIAL);
            factTranslationRepo.updateFactOfDayStatus(CURRENT, USED);
            list = factTranslationRepo.findRandomFact();
        }
        factTranslationRepo.updateFactOfDayStatusByHabitfactId(CURRENT, list.get().get(0).getHabitFact().getId());
    }

    /**
     * Clear fact of the day cache at 0:00 am every day.
     */
    @CacheEvict(value = FACT_OF_THE_DAY_CACHE_NAME, allEntries = true)
    @Transactional
    @Scheduled(cron = "0 0 0 * * *")
    public void chooseNewFactOfTheDay() {
    }

    /**
     * Every day at 00:00 deletes from the database users
     * that have status 'DEACTIVATED' and last visited the site 2 years ago.
     *
     * @author Vasyl Zhovnir
     **/
    @Scheduled(cron = "0 0 0 * * *")
    @Transactional
    public void scheduleDeleteDeactivatedUsers() {
        userRepo.scheduleDeleteDeactivatedUsers();
    }
}
