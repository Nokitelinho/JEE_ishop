package net.devstudy.ishop.service.impl;

import java.io.UnsupportedEncodingException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.annotation.PreDestroy;
import javax.mail.MessagingException;
import javax.mail.internet.InternetAddress;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMailMessage;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import net.devstudy.ishop.service.NotificationService;

/**
 * 
 * @author devstudy
 * @see http://devstudy.net
 */
@Service
public class AsyncEmailNotificationService implements NotificationService {
	private static final Logger LOGGER = LoggerFactory.getLogger(AsyncEmailNotificationService.class);
	private final ExecutorService executorService;
	
	@Autowired
	private JavaMailSender javaMailSender;
	
	@Value("email.smtp.fromEmail")
	private String fromEmail;
	
	@Value("${email.smtp.tryCount}")
	private String tryCount;

	public AsyncEmailNotificationService() {
		executorService = Executors.newCachedThreadPool();
	}

	@Override
	public void sendNotificationMessage(String notificationAddress, String content) {
		executorService.submit(new EmailItem(notificationAddress, "New order", content, Integer.parseInt(tryCount)));
	}

	@PreDestroy
	public void close() {
		executorService.shutdown();
	}

	/**
	 * 
	 * 
	 * @author devstudy
	 * @see http://devstudy.net
	 */
	private class EmailItem implements Runnable {
		private final String emailAddress;
		private final String subject;
		private final String content;
		private int tryCount;

		private EmailItem(String emailAddress, String subject, String content, int tryCount) {
			super();
			this.emailAddress = emailAddress;
			this.subject = subject;
			this.content = content;
			this.tryCount = tryCount;
		}

		private boolean isValidTryCount() {
			return tryCount > 0;
		}

		@Override
		public void run() {
			try {
				MimeMailMessage msg = buildMessage(subject, content, emailAddress);
				javaMailSender.send(msg.getMimeMessage());
			} catch (Exception e) {
				LOGGER.error("Can't send email: " + e.getMessage(), e);
				tryCount--;
				if (isValidTryCount()) {
					LOGGER.info("Resend email: {}", this.toString());
					executorService.submit(this);
				} else {
					LOGGER.error("Email was not sent: limit of try count");
				}
			}
		}
		
		protected MimeMailMessage buildMessage(String subject, String content, String email) throws MessagingException, UnsupportedEncodingException{
			MimeMessageHelper message = new MimeMessageHelper(javaMailSender.createMimeMessage(), false);
			message.setSubject(subject);
			message.setTo(new InternetAddress(email, ""));
			message.setFrom(fromEmail, "");
			message.setText(content);
			return new MimeMailMessage(message);
		}
	}
}
