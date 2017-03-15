package com.fafe.service;

import java.util.Properties;

import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

import org.apache.commons.configuration.ConfigurationException;

import com.fafe.core.properties.ConfigLoader;
import com.fafe.core.properties.ConfigProperty;

public class MailService {
	private final String host, port, emailid, username, password;
	private final Properties props = System.getProperties();
	private final Session l_session;

	public MailService() throws ConfigurationException {
		ConfigLoader cl = new ConfigLoader();

		host = cl.getStrProperty(ConfigProperty.MAIL_HOST);// "smtp.mail.yahoo.com";
		port = cl.getStrProperty(ConfigProperty.MAIL_PORT);// "587";
		emailid = cl.getStrProperty(ConfigProperty.MAIL_ID);// "fafe77@yahoo.com";
		username = cl.getStrProperty(ConfigProperty.MAIL_USERNAME);// "fafe77";
		password = cl.getStrProperty(ConfigProperty.MAIL_PWD);// "0403CbR!Y";

		emailSettings();
		l_session = createSession();
	}

	public void emailSettings() {
		props.put("mail.smtp.host", host);
		props.put("mail.smtp.auth", "true");
		props.put("mail.debug", "false");
		props.put("mail.smtp.port", port);
		props.put("mail.smtp.starttls.enable", "true");
		// props.put("mail.smtp.socketFactory.port", port);
		// props.put("mail.smtp.socketFactory.class",
		// "javax.net.ssl.SSLSocketFactory");
		// props.put("mail.smtp.socketFactory.fallback", "false");
	}

	public Session createSession() {
		Session _session = Session.getDefaultInstance(props, new javax.mail.Authenticator() {
			protected PasswordAuthentication getPasswordAuthentication() {
				return new PasswordAuthentication(username, password);
			}
		});
		// _session.setDebug(true); // Enable the debug mode
		return _session;
	}

	public boolean sendMessage(String toEmail, String subject, String msg) {
		// System.out.println("Inside sendMessage 2 :: >> ");
		try {
			// System.out.println("Sending Message
			// *********************************** ");
			MimeMessage message = new MimeMessage(l_session);
			// System.out.println("mail id in property =============
			// >>>>>>>>>>>>>> " + emailid);
			// message.setFrom(new InternetAddress(emailid));
			message.setFrom(new InternetAddress(this.emailid));

			message.addRecipient(Message.RecipientType.TO, new InternetAddress(toEmail));
			// message.addRecipient(Message.RecipientType.BCC, new
			// InternetAddress(AppConstants.fromEmail));
			message.setSubject(subject);
			message.setContent(msg, "text/html");

			// message.setText(msg);
			Transport.send(message);
			System.out.println("Message Sent");
		} catch (MessagingException mex) {
			mex.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		} // end catch block
		return true;
	}

	public static void main(String[] args) {
		try {
			new MailService().sendMessage("fabiocabrini77@gmail.com", "Order generated", "test Mail");
		} catch (ConfigurationException e) {
			e.printStackTrace();
		}
	}
}
