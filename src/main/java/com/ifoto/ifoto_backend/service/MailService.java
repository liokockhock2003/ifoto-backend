package com.ifoto.ifoto_backend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MailService {

    private final JavaMailSender mailSender;

    @Value("${app.mail.from}")
    private String from;

    @Value("${app.mail.app-name:iFoto}")
    private String appName;

    public void sendVerificationEmail(String toEmail, String verificationLink) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(toEmail);
        message.setFrom(from);
        message.setSubject(appName + " - Verify Your Email Address");
        message.setText(buildVerificationBody(verificationLink));
        mailSender.send(message);
    }

    private String buildVerificationBody(String verificationLink) {
        return "Welcome to " + appName + "!\n\n"
                + "Please verify your email address by clicking the link below:\n"
                + verificationLink
                + "\n\n"
                + "This link will expire in 24 hours.\n\n"
                + "If you did not create an account, please ignore this email.";
    }

    public void sendPasswordResetEmail(String toEmail, String resetLink) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(toEmail);
        message.setFrom(from);
        message.setSubject(appName + " Password Reset Request");
        message.setText(buildResetPasswordBody(resetLink));
        mailSender.send(message);
    }

    private String buildResetPasswordBody(String resetLink) {
        return "We received a request to reset your password.\n\n"
                + "Use the link below to set a new password:\n"
                + resetLink
                + "\n\n"
                + "If you did not request this, please ignore this email.";
    }

    public void sendRentalSubmittedToCommittee(List<String> committeeEmails, String rentalNumber,
            String renterName, List<String> equipmentList, LocalDate start, LocalDate end) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(committeeEmails.toArray(new String[0]));
        message.setFrom(from);
        message.setSubject(appName + " - New Rental Request " + rentalNumber);
        message.setText("A new rental request has been submitted.\n\n"
                + "Rental: " + rentalNumber + "\n"
                + "Renter: " + renterName + "\n"
                + "Requested dates: " + start + " to " + end + "\n"
                + "Equipment:\n" + String.join("\n", equipmentList.stream().map(e -> "  - " + e).toList())
                + "\n\nPlease log in to review.");
        mailSender.send(message);
    }

    public void sendRentalApprovedToRenter(String renterEmail, String rentalNumber,
            Long totalAmountCents, LocalDate approvedStart, LocalDate approvedEnd) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(renterEmail);
        message.setFrom(from);
        message.setSubject(appName + " - Rental Approved: " + rentalNumber);
        message.setText("Your rental request " + rentalNumber + " has been approved.\n\n"
                + "Approved dates: " + approvedStart + " to " + approvedEnd + "\n"
                + "Total amount: RM " + String.format("%.2f", totalAmountCents / 100.0)
                + "\n\nPlease log in to proceed with payment.");
        mailSender.send(message);
    }

    public void sendRentalRejectedToRenter(String renterEmail, String rentalNumber, String reason) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(renterEmail);
        message.setFrom(from);
        message.setSubject(appName + " - Rental Request Rejected: " + rentalNumber);
        message.setText("Your rental request " + rentalNumber + " has been rejected.\n\n"
                + "Reason: " + reason
                + "\n\nContact the Equipment Committee for more information.");
        mailSender.send(message);
    }

    public void sendPaymentConfirmedToRenter(String renterEmail, String rentalNumber, String receiptNumber) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(renterEmail);
        message.setFrom(from);
        message.setSubject(appName + " - Payment Confirmed: " + rentalNumber);
        message.setText("Your payment for rental " + rentalNumber + " has been confirmed.\n\n"
                + "Receipt number: " + receiptNumber
                + "\n\nThank you. Please collect your equipment on the approved start date.");
        mailSender.send(message);
    }

    public void sendCashPaymentPendingToCommittee(List<String> committeeEmails, String rentalNumber,
            String renterName, Long amountCents) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(committeeEmails.toArray(new String[0]));
        message.setFrom(from);
        message.setSubject(appName + " - Cash Payment Pending: " + rentalNumber);
        message.setText("A renter has selected cash payment for rental " + rentalNumber + ".\n\n"
                + "Renter: " + renterName + "\n"
                + "Amount: RM " + String.format("%.2f", amountCents / 100.0)
                + "\n\nPlease confirm cash receipt in the system once collected.");
        mailSender.send(message);
    }
}
