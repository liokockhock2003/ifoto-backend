package com.ifoto.ifoto_backend.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class MailService {

    private final JavaMailSender mailSender;

    @Value("${app.mail.from}")
    private String from;

    @Value("${app.mail.app-name:iFoto}")
    private String appName;

    // ── Public methods ────────────────────────────────────────────────────────

    @Async
    public void sendVerificationEmail(String toEmail, String verificationLink) {
        String body = h2("Verify Your Email Address")
                + p("Welcome to " + appName + "! Please verify your email address to activate your account.")
                + button("Verify Email", verificationLink, "#2563eb")
                + note("This link expires in 24 hours. If you did not create an account, you can safely ignore this email.");
        send(toEmail, appName + " – Verify Your Email Address", wrap("Verify Email", body));
    }

    @Async
    public void sendPasswordResetEmail(String toEmail, String resetLink) {
        String body = h2("Password Reset Request")
                + p("We received a request to reset your password. Click the button below to set a new password.")
                + button("Reset Password", resetLink, "#2563eb")
                + note("This link expires in 15 minutes. If you did not request a password reset, please ignore this email — your account remains secure.");
        send(toEmail, appName + " – Password Reset Request", wrap("Password Reset", body));
    }

    @Async
    public void sendRentalSubmittedToCommittee(List<String> committeeEmails, String rentalNumber,
            String renterName, List<String> equipmentList, LocalDate start, LocalDate end) {
        StringBuilder equipmentRows = new StringBuilder();
        for (String eq : equipmentList) {
            equipmentRows.append("<li style='padding:4px 0;color:#374151;font-size:14px;'>").append(eq).append("</li>");
        }
        String body = badge("New Request", "#dbeafe", "#1e40af")
                + h2("New Rental Request")
                + infoTable(new String[][]{
                    {"Rental Number", rentalNumber},
                    {"Submitted By",  renterName},
                    {"Requested Dates", start + " → " + end}
                })
                + "<p style='margin:24px 0 8px;color:#374151;font-size:14px;font-weight:600;'>Equipment Requested</p>"
                + "<ul style='margin:0;padding-left:20px;'>" + equipmentRows + "</ul>"
                + p("Please log in to the system to review and approve or reject this request.");
        sendToMany(committeeEmails, appName + " – New Rental Request " + rentalNumber, wrap("New Rental Request", body));
    }

    @Async
    public void sendRentalApprovedToRenter(String renterEmail, String rentalNumber,
            Long totalAmountCents, LocalDate approvedStart, LocalDate approvedEnd) {
        String body = badge("Approved", "#dcfce7", "#166534")
                + h2("Your Rental Has Been Approved")
                + infoTable(new String[][]{
                    {"Rental Number",   rentalNumber},
                    {"Approved Dates",  approvedStart + " → " + approvedEnd},
                    {"Total Amount",    "RM " + String.format("%.2f", totalAmountCents / 100.0)}
                })
                + p("Please log in to proceed with payment. Your equipment will be reserved once payment is completed.");
        send(renterEmail, appName + " – Rental Approved: " + rentalNumber, wrap("Rental Approved", body));
    }

    @Async
    public void sendRentalRejectedToRenter(String renterEmail, String rentalNumber, String reason) {
        String body = badge("Rejected", "#fee2e2", "#991b1b")
                + h2("Rental Request Not Approved")
                + infoTable(new String[][]{
                    {"Rental Number", rentalNumber},
                    {"Reason",        reason}
                })
                + p("If you have questions or would like to submit a new request, please contact the Equipment Committee.");
        send(renterEmail, appName + " – Rental Request Rejected: " + rentalNumber, wrap("Rental Rejected", body));
    }

    @Async
    public void sendPaymentConfirmedToRenter(String renterEmail, String rentalNumber, String receiptNumber) {
        String body = badge("Payment Confirmed", "#dcfce7", "#166534")
                + h2("Payment Received")
                + infoTable(new String[][]{
                    {"Rental Number", rentalNumber},
                    {"Receipt Number", receiptNumber}
                })
                + p("Thank you for your payment. Please collect your equipment on the approved start date.");
        send(renterEmail, appName + " – Payment Confirmed: " + rentalNumber, wrap("Payment Confirmed", body));
    }

    @Async
    public void sendCashPaymentPendingToCommittee(List<String> committeeEmails, String rentalNumber,
            String renterName, Long amountCents) {
        String body = badge("Cash Payment Pending", "#fef9c3", "#854d0e")
                + h2("Cash Payment Awaiting Confirmation")
                + infoTable(new String[][]{
                    {"Rental Number", rentalNumber},
                    {"Renter",        renterName},
                    {"Amount Due",    "RM " + String.format("%.2f", amountCents / 100.0)}
                })
                + p("The renter has selected cash payment. Please confirm receipt of payment in the system once collected.");
        sendToMany(committeeEmails, appName + " – Cash Payment Pending: " + rentalNumber, wrap("Cash Payment Pending", body));
    }

    // ── Private send helpers ──────────────────────────────────────────────────

    private void send(String to, String subject, String html) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(from);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);
            mailSender.send(message);
        } catch (MessagingException e) {
            log.error("Failed to send email to {}: {}", to, e.getMessage());
        }
    }

    private void sendToMany(List<String> recipients, String subject, String html) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(from);
            helper.setTo(recipients.toArray(new String[0]));
            helper.setSubject(subject);
            helper.setText(html, true);
            mailSender.send(message);
        } catch (MessagingException e) {
            log.error("Failed to send email to {}: {}", recipients, e.getMessage());
        }
    }

    // ── HTML template builders ────────────────────────────────────────────────

    private String wrap(String title, String bodyHtml) {
        return "<!DOCTYPE html><html lang='en'><head>"
                + "<meta charset='UTF-8'>"
                + "<meta name='viewport' content='width=device-width,initial-scale=1'>"
                + "<title>" + title + "</title></head>"
                + "<body style='margin:0;padding:0;background-color:#f3f4f6;"
                + "font-family:\"Helvetica Neue\",Helvetica,Arial,sans-serif;'>"
                + "<table width='100%' cellpadding='0' cellspacing='0' border='0'"
                + " style='background-color:#f3f4f6;'><tr>"
                + "<td align='center' style='padding:40px 16px;'>"
                + "<table width='600' cellpadding='0' cellspacing='0' border='0'"
                + " style='max-width:600px;width:100%;'>"

                // Header
                + "<tr><td style='background-color:#111827;border-radius:8px 8px 0 0;"
                + "padding:32px;text-align:center;'>"
                + "<p style='margin:0;color:#ffffff;font-size:26px;font-weight:700;"
                + "letter-spacing:4px;'>" + appName + "</p>"
                + "<p style='margin:6px 0 0;color:#9ca3af;font-size:12px;"
                + "letter-spacing:2px;text-transform:uppercase;'>Photography Club</p>"
                + "</td></tr>"

                // Body card
                + "<tr><td style='background-color:#ffffff;padding:40px 48px;"
                + "border-radius:0 0 8px 8px;'>"
                + bodyHtml
                + "</td></tr>"

                // Footer
                + "<tr><td style='padding:24px 16px;text-align:center;'>"
                + "<p style='margin:0;color:#9ca3af;font-size:12px;'>"
                + "© 2026 " + appName + " Photography Club. All rights reserved.</p>"
                + "<p style='margin:6px 0 0;color:#9ca3af;font-size:12px;'>"
                + "This is an automated message — please do not reply.</p>"
                + "</td></tr>"

                + "</table></td></tr></table></body></html>";
    }

    private String h2(String text) {
        return "<h2 style='margin:16px 0 8px;color:#111827;font-size:22px;"
                + "font-weight:700;'>" + text + "</h2>";
    }

    private String p(String text) {
        return "<p style='margin:16px 0;color:#4b5563;font-size:15px;line-height:1.6;'>"
                + text + "</p>";
    }

    private String note(String text) {
        return "<p style='margin:24px 0 0;color:#9ca3af;font-size:13px;line-height:1.6;"
                + "border-top:1px solid #f3f4f6;padding-top:16px;'>" + text + "</p>";
    }

    private String badge(String text, String bgColor, String textColor) {
        return "<p style='margin:0 0 16px;'><span style='display:inline-block;"
                + "background-color:" + bgColor + ";color:" + textColor + ";"
                + "font-size:12px;font-weight:700;letter-spacing:1px;text-transform:uppercase;"
                + "padding:4px 12px;border-radius:999px;'>" + text + "</span></p>";
    }

    private String button(String text, String href, String bgColor) {
        return "<table cellpadding='0' cellspacing='0' border='0' style='margin:28px auto;'><tr>"
                + "<td style='background-color:" + bgColor + ";border-radius:6px;text-align:center;'>"
                + "<a href='" + href + "' style='display:inline-block;padding:14px 36px;"
                + "color:#ffffff;font-size:15px;font-weight:600;text-decoration:none;"
                + "letter-spacing:0.3px;'>" + text + "</a>"
                + "</td></tr></table>";
    }

    private String infoTable(String[][] rows) {
        StringBuilder sb = new StringBuilder();
        sb.append("<table width='100%' cellpadding='0' cellspacing='0' border='0'"
                + " style='margin:20px 0;border-radius:6px;overflow:hidden;"
                + "border:1px solid #e5e7eb;'>");
        for (String[] row : rows) {
            sb.append("<tr>")
              .append("<td style='padding:12px 16px;background-color:#f9fafb;"
                    + "color:#6b7280;font-size:13px;font-weight:600;text-transform:uppercase;"
                    + "letter-spacing:0.5px;border-bottom:1px solid #e5e7eb;"
                    + "white-space:nowrap;width:38%;'>").append(row[0]).append("</td>")
              .append("<td style='padding:12px 16px;background-color:#ffffff;"
                    + "color:#111827;font-size:14px;border-bottom:1px solid #e5e7eb;'>")
                    .append(row[1]).append("</td>")
              .append("</tr>");
        }
        sb.append("</table>");
        return sb.toString();
    }
}
