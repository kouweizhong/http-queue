package br.com.http.timer;

import javax.annotation.Resource;
import javax.ejb.NoMoreTimeoutsException;
import javax.ejb.ScheduleExpression;
import javax.ejb.Stateless;
import javax.ejb.Timeout;
import javax.ejb.Timer;
import javax.ejb.TimerConfig;
import javax.ejb.TimerHandle;
import javax.ejb.TimerService;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import br.com.http.timer.exception.JobAlreadyExistsException;

@Stateless
public class JobManager {

	private static final Logger logger = LoggerFactory.getLogger(JobManager.class);

	@Resource
	private TimerService timerService;

	@PersistenceContext(unitName = "primary")
	private EntityManager em;

	@Inject
	private JobExecutor executor;

	public Job createJob(Job job) throws JobAlreadyExistsException {
		if (job.getId() != null && em.find(Job.class, job.getId()) != null) {
			throw new JobAlreadyExistsException();
		}

		ScheduleExpression schedule = new ScheduleExpression();
		schedule.second(job.getSecond());
		schedule.minute(job.getMinute());
		schedule.hour(job.getHour());
		schedule.dayOfMonth(job.getDayOfMonth());
		schedule.dayOfWeek(job.getDayOfWeek());
		schedule.month(job.getMonth());
		schedule.year(job.getYear());

		TimerConfig timerConfig = new TimerConfig(job.getId(), true);
		Timer timer = timerService.createCalendarTimer(schedule, timerConfig);
		TimerHandle timerHandle = timer.getHandle();
		job.serialize(timerHandle);

		logger.info("Timer {} created with cron expression {}. The next timeout is {}.", job.getId(),
				job.getCronExpression(), timer.getNextTimeout());

		if (job.getId() != null) {
			em.merge(job);
		} else {
			em.persist(job);
		}

		return job;
	}

	public void removeJob(long jobId) {
		Job job = em.find(Job.class, jobId);
		if (job != null) {
			em.remove(job);
			TimerHandle timerHandle = job.geTimerHandle();
			if (timerHandle != null) {
				timerHandle.getTimer().cancel();
			}
		} else {
			logger.info("Job with id {} not found. Cancelling...", jobId);
		}
	}

	@Timeout
	public void execute(Timer timer) {
		try {
			if (timer.getTimeRemaining() < 0) {
				logger.info("Skipping missed job timeout with id {}", timer.getInfo());
				return;
			}
		} catch (NoMoreTimeoutsException e) {
		}

		Job job = em.find(Job.class, timer.getInfo());
		if (job == null) {
			logger.info("Job with id {} not found. Cancelling...", timer.getInfo());
			timer.cancel();
		} else if (!job.isActivate()) {
			logger.info("Skipping execution of job {} because it is marked as inactive.", timer.getInfo());
		} else {
			JobExecution execution = executor.createExecution(job);
			executor.execute(execution, job);
		}
	}
}
