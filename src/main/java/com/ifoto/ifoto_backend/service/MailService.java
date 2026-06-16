package com.ifoto.ifoto_backend.service;

import jakarta.annotation.PostConstruct;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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

    @Value("${app.mail.dev-override-recipient:}")
    private String devOverrideRecipient;

    @Value("${app.frontend.login-url:http://localhost:5173/login}")
    private String loginUrl;

    // ── KFK brand palette (from index.css) ───────────────────────────────────
    private static final String C_BRAND = "#680202"; // --brand
    private static final String C_HEADER_BG = "#4a0101"; // darker brand red for email header
    private static final String C_HEADER_SUB = "#fcb8b8"; // appName subtitle on dark red header
    private static final String C_PAGE_BG = "#f8f4ef"; // --background (warm off-white)
    private static final String C_CARD_BG = "#ffffff"; // --card
    private static final String C_BORDER = "#e8ddd0"; // --border (warm)
    private static final String C_FOREGROUND = "#1c1a18"; // --foreground
    private static final String C_TEXT = "#374151"; // body paragraph text
    private static final String C_MUTED_BG = "#f5f0e8"; // --muted (table label cell bg)
    private static final String C_MUTED_FG = "#6b6560"; // --muted-foreground
    private static final String C_SUBTLE = "#9ca3af"; // footer / note text

    private static final String C_BADGE_SUCCESS_BG = "#dcfce7"; // --badge-success-bg
    private static final String C_BADGE_SUCCESS_FG = "#166534"; // --badge-success-fg
    private static final String C_BADGE_INFO_BG = "#dbeafe"; // --badge-info-bg
    private static final String C_BADGE_INFO_FG = "#1e40af"; // --badge-info-fg
    private static final String C_BADGE_WARN_BG = "#fef9c3"; // --badge-warning-bg
    private static final String C_BADGE_WARN_FG = "#854d0e"; // --badge-warning-fg
    private static final String C_BADGE_DANGER_BG = "#fee2e2"; // --badge-danger-bg
    private static final String C_BADGE_DANGER_FG = "#991b1b"; // --badge-danger-fg

    private boolean logoAvailable = false;

    @PostConstruct
    void checkLogo() {
        logoAvailable = new ClassPathResource("static/images/KFKLogo.png").exists();
    }

    // ── Public methods ────────────────────────────────────────────────────────

    @Async
    public void sendVerificationEmail(String toEmail, String verificationLink) {
        String body = h2("Verify Your Email Address")
                + p("Welcome to " + appName + "! Please verify your email address to activate your account.")
                + button("Verify Email", verificationLink, C_BRAND)
                + note("This link expires in 24 hours. If you did not create an account, you can safely ignore this email.");
        send(toEmail, appName + " – Verify Your Email Address", wrap("Verify Email", body));
    }

    @Async
    public void sendPasswordResetEmail(String toEmail, String resetLink) {
        String body = h2("Password Reset Request")
                + p("We received a request to reset your password. Click the button below to set a new password.")
                + button("Reset Password", resetLink, C_BRAND)
                + note("This link expires in 15 minutes. If you did not request a password reset, please ignore this email — your account remains secure.");
        send(toEmail, appName + " – Password Reset Request", wrap("Password Reset", body));
    }

    @Async
    public void sendRentalSubmittedToCommittee(List<String> committeeEmails, String rentalNumber,
            String renterName, List<String[]> mainEquipmentRows, List<String[]> subEquipmentRows,
            LocalDate start, LocalDate end) {
        List<String[]> combined = new java.util.ArrayList<>(mainEquipmentRows);
        if (subEquipmentRows != null) combined.addAll(subEquipmentRows);
        String[][] equipmentTable = combined.toArray(new String[0][]);
        String body = badge("New Request", C_BADGE_INFO_BG, C_BADGE_INFO_FG)
                + h2("New Rental Request")
                + infoTable(new String[][] {
                        { "Rental Number",   rentalNumber },
                        { "Submitted By",    renterName },
                        { "Requested Dates", fmtDate(start) + " to " + fmtDate(end) }
                })
                + "<p style='margin:24px 0 8px;color:" + C_FOREGROUND
                + ";font-size:14px;font-weight:600;'>Equipment Requested</p>"
                + infoTable(equipmentTable)
                + p("Please log in to the system to review and approve or reject this request.")
                + loginButton();
        sendToMany(committeeEmails, appName + " – New Rental Request " + rentalNumber,
                wrap("New Rental Request", body));
    }

    @Async
    public void sendRentalApprovedToRenter(String renterEmail, String rentalNumber,
            Long totalAmountCents, LocalDate programStart, LocalDate programEnd,
            LocalDateTime pickupDatetime, LocalDateTime returnDatetime) {
        String body = badge("Approved", C_BADGE_SUCCESS_BG, C_BADGE_SUCCESS_FG)
                + h2("Your Rental Has Been Approved")
                + infoTable(new String[][] {
                        { "Rental Number", rentalNumber },
                        { "Program Dates", fmtDate(programStart) + " to " + fmtDate(programEnd) },
                        { "Pickup By",     fmt(pickupDatetime) },
                        { "Return By",     fmt(returnDatetime) },
                        { "Total Amount",  "RM " + String.format("%.2f", totalAmountCents / 100.0) }
                })
                + p("Your equipment will be ready for pickup on the scheduled date. Payment can be made once the committee confirms your pickup. Please log in to your account to view your invoice.")
                + loginButton();
        send(renterEmail, appName + " – Rental Approved: " + rentalNumber, wrap("Rental Approved", body));
    }

    @Async
    public void sendEquipmentPickedUpToRenter(String renterEmail, String rentalNumber,
            LocalDateTime pickedUpAt) {
        String body = badge("Picked Up", C_BADGE_SUCCESS_BG, C_BADGE_SUCCESS_FG)
                + h2("Equipment Handed Over")
                + infoTable(new String[][] {
                        { "Rental Number", rentalNumber },
                        { "Pickup Time", fmt(pickedUpAt) }
                })
                + p("Your equipment has been handed over. Please log in to the system to complete your payment.")
                + loginButton();
        send(renterEmail, appName + " – Equipment Picked Up: " + rentalNumber, wrap("Equipment Picked Up", body));
    }

    @Async
    public void sendLogisticsUpdatedToRenter(String renterEmail, String rentalNumber,
            LocalDateTime pickupDatetime, LocalDateTime returnDatetime) {
        String body = badge("Schedule Updated", C_BADGE_WARN_BG, C_BADGE_WARN_FG)
                + h2("Rental Schedule Has Been Updated")
                + infoTable(new String[][] {
                        { "Rental Number", rentalNumber },
                        { "New Pickup", fmt(pickupDatetime) },
                        { "New Return By", fmt(returnDatetime) }
                })
                + p("Please check the updated schedule in the system.");
        send(renterEmail, appName + " – Schedule Updated: " + rentalNumber, wrap("Schedule Updated", body));
    }

    @Async
    public void sendEquipmentUpdatedToRenter(String renterEmail, String rentalNumber,
            List<String[]> equipmentRows) {
        String body = badge("Equipment Updated", C_BADGE_INFO_BG, C_BADGE_INFO_FG)
                + h2("Your Rental Equipment Has Been Updated")
                + infoTable(new String[][] { { "Rental Number", rentalNumber } })
                + "<p style='margin:24px 0 8px;color:" + C_FOREGROUND
                + ";font-size:14px;font-weight:600;'>Updated Equipment</p>"
                + infoTable(equipmentRows.toArray(new String[0][]))
                + p("Please log in to view your updated invoice.")
                + loginButton();
        send(renterEmail, appName + " – Equipment Updated: " + rentalNumber, wrap("Equipment Updated", body));
    }

    @Async
    public void sendRentalRejectedToRenter(String renterEmail, String rentalNumber, String reason) {
        String body = badge("Rejected", C_BADGE_DANGER_BG, C_BADGE_DANGER_FG)
                + h2("Rental Request Not Approved")
                + infoTable(new String[][] {
                        { "Rental Number", rentalNumber },
                        { "Reason", reason }
                })
                + p("If you have questions or would like to submit a new request, please contact the Equipment Committee.");
        send(renterEmail, appName + " – Rental Request Rejected: " + rentalNumber, wrap("Rental Rejected", body));
    }

    @Async
    public void sendPaymentConfirmedToRenter(String renterEmail, String rentalNumber, String receiptNumber) {
        String body = badge("Payment Confirmed", C_BADGE_SUCCESS_BG, C_BADGE_SUCCESS_FG)
                + h2("Payment Received")
                + infoTable(new String[][] {
                        { "Rental Number", rentalNumber },
                        { "Receipt Number", receiptNumber }
                })
                + p("Thank you for your payment. Your rental is now confirmed and your equipment is yours for the rental period — please remember to return it on time.")
                + loginButton();
        send(renterEmail, appName + " – Payment Confirmed: " + rentalNumber, wrap("Payment Confirmed", body));
    }

    @Async
    public void sendPaymentConfirmedToCommittee(String committeeEmail, String rentalNumber,
            String renterName, String receiptNumber) {
        String body = badge("Payment Confirmed", C_BADGE_INFO_BG, C_BADGE_INFO_FG)
                + h2("Rental Payment Received")
                + infoTable(new String[][] {
                        { "Rental Number", rentalNumber },
                        { "Renter", renterName },
                        { "Receipt Number", receiptNumber }
                })
                + p("Payment has been received for a rental you approved. No further action is needed.")
                + loginButton();
        send(committeeEmail, appName + " – Payment Received: " + rentalNumber, wrap("Payment Received", body));
    }

    @Async
    public void sendOverduePaymentConfirmedToRenter(String renterEmail, String rentalNumber, String receiptNumber) {
        String body = badge("Penalty Paid", C_BADGE_SUCCESS_BG, C_BADGE_SUCCESS_FG)
                + h2("Overdue Penalty Settled")
                + infoTable(new String[][] {
                        { "Rental Number", rentalNumber },
                        { "Receipt Number", receiptNumber }
                })
                + p("Thank you for settling the overdue penalty. This rental is now fully closed. We hope to see you again soon!")
                + loginButton();
        send(renterEmail, appName + " – Overdue Penalty Paid: " + rentalNumber, wrap("Overdue Penalty Paid", body));
    }

    @Async
    public void sendOverduePaymentConfirmedToCommittee(String committeeEmail, String rentalNumber,
            String renterName, String receiptNumber) {
        String body = badge("Penalty Paid", C_BADGE_INFO_BG, C_BADGE_INFO_FG)
                + h2("Overdue Penalty Settled")
                + infoTable(new String[][] {
                        { "Rental Number", rentalNumber },
                        { "Renter", renterName },
                        { "Receipt Number", receiptNumber }
                })
                + p("The overdue penalty has been paid for a rental you approved. This rental is now fully closed.")
                + loginButton();
        send(committeeEmail, appName + " – Overdue Penalty Paid: " + rentalNumber, wrap("Overdue Penalty Paid", body));
    }

    @Async
    public void sendCashPaymentPendingToCommittee(List<String> committeeEmails, String rentalNumber,
            String renterName, Long amountCents) {
        String body = badge("Cash Payment Pending", C_BADGE_WARN_BG, C_BADGE_WARN_FG)
                + h2("Cash Payment Awaiting Confirmation")
                + infoTable(new String[][] {
                        { "Rental Number", rentalNumber },
                        { "Renter", renterName },
                        { "Amount Due", "RM " + String.format("%.2f", amountCents / 100.0) }
                })
                + p("The renter has selected cash payment. Please confirm receipt of payment in the system once collected.");
        sendToMany(committeeEmails, appName + " – Cash Payment Pending: " + rentalNumber,
                wrap("Cash Payment Pending", body));
    }

    @Async
    public void sendRentalCancelledToRenter(String renterEmail, String rentalNumber) {
        String body = badge("Cancelled", C_BADGE_DANGER_BG, C_BADGE_DANGER_FG)
                + h2("Your Rental Has Been Cancelled")
                + infoTable(new String[][] { { "Rental Number", rentalNumber } })
                + p("Your rental request has been cancelled and the reserved equipment has been released. "
                        + "If this was a mistake, you are welcome to submit a new request.");
        send(renterEmail, appName + " – Rental Cancelled: " + rentalNumber, wrap("Rental Cancelled", body));
    }

    @Async
    public void sendRentalCancelledToCommittee(List<String> committeeEmails, String rentalNumber,
            String renterName) {
        String body = badge("Cancelled", C_BADGE_DANGER_BG, C_BADGE_DANGER_FG)
                + h2("A Rental Has Been Cancelled")
                + infoTable(new String[][] {
                        { "Rental Number", rentalNumber },
                        { "Renter", renterName }
                })
                + p("The renter has cancelled this rental. The reserved equipment is now available again.");
        sendToMany(committeeEmails, appName + " – Rental Cancelled: " + rentalNumber,
                wrap("Rental Cancelled", body));
    }

    @Async
    public void sendRentalReturnReminderToRenter(String renterEmail, String rentalNumber,
            LocalDateTime returnDatetime) {
        String body = badge("Return Reminder", C_BADGE_INFO_BG, C_BADGE_INFO_FG)
                + h2("Friendly Reminder: Equipment Due Today")
                + infoTable(new String[][] {
                        { "Rental Number", rentalNumber },
                        { "Return By", fmt(returnDatetime) }
                })
                + p("This is a gentle reminder that your rented equipment is due back today. "
                        + "Please return it on time to avoid late penalty charges.");
        send(renterEmail, appName + " – Return Reminder: " + rentalNumber, wrap("Return Reminder", body));
    }

    @Async
    public void sendRentalOverdueToRenter(String renterEmail, String rentalNumber,
            LocalDateTime returnDatetime, Long penaltyAmountCents) {
        String body = badge("Overdue", C_BADGE_DANGER_BG, C_BADGE_DANGER_FG)
                + h2("Action Required: Your Rental Is Overdue")
                + infoTable(new String[][] {
                        { "Rental Number", rentalNumber },
                        { "Was Due", fmt(returnDatetime) },
                        { "Penalty So Far", "RM " + String.format("%.2f", (penaltyAmountCents != null
                                ? penaltyAmountCents : 0L) / 100.0) }
                })
                + p("Your rented equipment is past its return date. A late penalty is accruing daily and "
                        + "will continue to grow until the equipment is returned. Please return it as soon as possible "
                        + "and settle the outstanding penalty.")
                + loginButton();
        send(renterEmail, appName + " – Rental Overdue: " + rentalNumber, wrap("Rental Overdue", body));
    }

    @Async
    public void sendRentalOverdueToCommittee(String committeeEmail, String rentalNumber,
            String renterName, LocalDateTime returnDatetime, Long penaltyAmountCents) {
        String body = badge("Overdue", C_BADGE_DANGER_BG, C_BADGE_DANGER_FG)
                + h2("A Rental You Approved Is Overdue")
                + infoTable(new String[][] {
                        { "Rental Number", rentalNumber },
                        { "Renter", renterName },
                        { "Was Due", fmt(returnDatetime) },
                        { "Penalty So Far", "RM " + String.format("%.2f", (penaltyAmountCents != null
                                ? penaltyAmountCents : 0L) / 100.0) }
                })
                + p("A rental you approved is past its return date and is accruing a daily late penalty. "
                        + "You may wish to follow up with the renter to arrange the return.")
                + loginButton();
        send(committeeEmail, appName + " – Rental Overdue: " + rentalNumber, wrap("Rental Overdue", body));
    }

    @Async
    public void sendRentalReturnedToRenter(String renterEmail, String rentalNumber,
            LocalDateTime returnedAt, Long penaltyAmountCents) {
        boolean hasPenalty = penaltyAmountCents != null && penaltyAmountCents > 0;
        String body = badge("Returned", C_BADGE_SUCCESS_BG, C_BADGE_SUCCESS_FG)
                + h2("Equipment Return Confirmed")
                + infoTable(hasPenalty
                        ? new String[][] {
                                { "Rental Number", rentalNumber },
                                { "Returned At", fmt(returnedAt) },
                                { "Outstanding Penalty", "RM " + String.format("%.2f", penaltyAmountCents / 100.0) }
                        }
                        : new String[][] {
                                { "Rental Number", rentalNumber },
                                { "Returned At", fmt(returnedAt) }
                        })
                + (hasPenalty
                        ? p("Thank you for returning the equipment. Because the return was late, an overdue penalty "
                                + "applies. Please log in to settle the outstanding penalty at your earliest convenience.")
                                + loginButton()
                        : p("Thank you for returning the equipment on time. We hope to see you again soon!"));
        send(renterEmail, appName + " – Rental Returned: " + rentalNumber, wrap("Rental Returned", body));
    }

    // ── Event equipment request notifications ─────────────────────────────────

    @Async
    public void sendEventRequestSubmittedToCommittee(List<String> committeeEmails, String requestNumber,
            String requesterName, String eventName, List<String[]> mainEquipmentRows,
            List<String[]> subEquipmentRows, LocalDateTime start, LocalDateTime end) {
        List<String[]> combined = new java.util.ArrayList<>(mainEquipmentRows);
        if (subEquipmentRows != null) combined.addAll(subEquipmentRows);
        String[][] equipmentTable = combined.toArray(new String[0][]);
        String body = badge("New Request", C_BADGE_INFO_BG, C_BADGE_INFO_FG)
                + h2("New Event Equipment Request")
                + infoTable(new String[][] {
                        { "Request Number", requestNumber },
                        { "Event", eventName },
                        { "Submitted By", requesterName },
                        { "Requested Period", fmt(start) + " to " + fmt(end) }
                })
                + "<p style='margin:24px 0 8px;color:" + C_FOREGROUND
                + ";font-size:14px;font-weight:600;'>Equipment Requested</p>"
                + infoTable(equipmentTable)
                + p("Please log in to the system to review and approve or reject this request.")
                + loginButton();
        sendToMany(committeeEmails, appName + " – New Event Equipment Request " + requestNumber,
                wrap("New Event Equipment Request", body));
    }

    @Async
    public void sendEventRequestApprovedToRequester(String requesterEmail, String requestNumber,
            String eventName, LocalDateTime pickupDatetime, LocalDateTime returnDatetime) {
        String body = badge("Approved", C_BADGE_SUCCESS_BG, C_BADGE_SUCCESS_FG)
                + h2("Your Equipment Request Has Been Approved")
                + infoTable(new String[][] {
                        { "Request Number", requestNumber },
                        { "Event", eventName },
                        { "Pickup By", fmt(pickupDatetime) },
                        { "Return By", fmt(returnDatetime) }
                })
                + p("Your equipment will be ready for collection on the scheduled date. "
                        + "Please log in to view the full details.")
                + loginButton();
        send(requesterEmail, appName + " – Equipment Request Approved: " + requestNumber,
                wrap("Equipment Request Approved", body));
    }

    @Async
    public void sendEventRequestRejectedToRequester(String requesterEmail, String requestNumber, String reason) {
        String body = badge("Rejected", C_BADGE_DANGER_BG, C_BADGE_DANGER_FG)
                + h2("Equipment Request Not Approved")
                + infoTable(new String[][] {
                        { "Request Number", requestNumber },
                        { "Reason", reason }
                })
                + p("If you have questions or would like to submit a new request, please contact the Equipment Committee.");
        send(requesterEmail, appName + " – Equipment Request Rejected: " + requestNumber,
                wrap("Equipment Request Rejected", body));
    }

    @Async
    public void sendEventRequestCancelledToCommittee(List<String> committeeEmails, String requestNumber,
            String requesterName) {
        String body = badge("Cancelled", C_BADGE_DANGER_BG, C_BADGE_DANGER_FG)
                + h2("An Equipment Request Has Been Cancelled")
                + infoTable(new String[][] {
                        { "Request Number", requestNumber },
                        { "Requested By", requesterName }
                })
                + p("The requester has cancelled this event equipment request. "
                        + "The reserved equipment is now available again.");
        sendToMany(committeeEmails, appName + " – Equipment Request Cancelled: " + requestNumber,
                wrap("Equipment Request Cancelled", body));
    }

    @Async
    public void sendEventRequestPickedUpToRequester(String requesterEmail, String requestNumber,
            LocalDateTime pickedUpAt) {
        String body = badge("Picked Up", C_BADGE_SUCCESS_BG, C_BADGE_SUCCESS_FG)
                + h2("Equipment Handed Over")
                + infoTable(new String[][] {
                        { "Request Number", requestNumber },
                        { "Pickup Time", fmt(pickedUpAt) }
                })
                + p("Your requested equipment has been handed over. Please take good care of it and "
                        + "return it by the agreed date.");
        send(requesterEmail, appName + " – Equipment Picked Up: " + requestNumber,
                wrap("Equipment Picked Up", body));
    }

    @Async
    public void sendEventRequestReturnedToRequester(String requesterEmail, String requestNumber,
            LocalDateTime returnedAt) {
        String body = badge("Returned", C_BADGE_SUCCESS_BG, C_BADGE_SUCCESS_FG)
                + h2("Equipment Return Confirmed")
                + infoTable(new String[][] {
                        { "Request Number", requestNumber },
                        { "Returned At", fmt(returnedAt) }
                })
                + p("Thank you for returning the equipment. We hope your event was a success!");
        send(requesterEmail, appName + " – Equipment Returned: " + requestNumber,
                wrap("Equipment Returned", body));
    }

    // ── Private send helpers ──────────────────────────────────────────────────

    private void send(String to, String subject, String html) {
        String effectiveTo = devOverrideRecipient != null && !devOverrideRecipient.isBlank()
                ? devOverrideRecipient : to;
        log.info("[Mail] Sending '{}' to {}", subject, effectiveTo);
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(from);
            helper.setTo(effectiveTo);
            helper.setSubject(subject);
            helper.setText(html, true);
            tryAddLogo(helper);
            mailSender.send(message);
            log.info("[Mail] Sent '{}' to {}", subject, effectiveTo);
        } catch (Exception e) {
            log.error("[Mail] Failed to send '{}' to {}: {}", subject, effectiveTo, e.getMessage(), e);
        }
    }

    private void sendToMany(List<String> recipients, String subject, String html) {
        String[] effectiveTo = devOverrideRecipient != null && !devOverrideRecipient.isBlank()
                ? new String[]{ devOverrideRecipient }
                : recipients.toArray(new String[0]);
        log.info("[Mail] Sending '{}' to {}", subject, java.util.Arrays.toString(effectiveTo));
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(from);
            helper.setTo(effectiveTo);
            helper.setSubject(subject);
            helper.setText(html, true);
            tryAddLogo(helper);
            mailSender.send(message);
            log.info("[Mail] Sent '{}' to {}", subject, java.util.Arrays.toString(effectiveTo));
        } catch (Exception e) {
            log.error("[Mail] Failed to send '{}' to {}: {}", subject, java.util.Arrays.toString(effectiveTo), e.getMessage(), e);
        }
    }

    private void tryAddLogo(MimeMessageHelper helper) {
        if (!logoAvailable)
            return;
        try {
            helper.addInline("logo", new ClassPathResource("static/images/KFKLogo.png"));
        } catch (Exception e) {
            log.debug("Could not attach KFK logo inline");
        }
    }

    // ── HTML template builders ────────────────────────────────────────────────

    private String wrap(String title, String bodyHtml) {
        String safeName = esc(appName);
        String logoHtml = logoAvailable
                ? "<div style='display:inline-block;background-color:#ffffff;"
                        + "border-radius:12px;padding:10px 18px;margin-bottom:14px;'>"
                        + "<img src='cid:logo' alt='KFK Logo' style='height:48px;width:auto;"
                        + "display:block;' /></div><br />"
                : "";
        return "<!DOCTYPE html><html lang='en'><head>"
                + "<meta charset='UTF-8'>"
                + "<meta name='viewport' content='width=device-width,initial-scale=1'>"
                + "<title>" + esc(title) + "</title></head>"
                + "<body style='margin:0;padding:0;background-color:" + C_PAGE_BG + ";"
                + "font-family:\"Helvetica Neue\",Helvetica,Arial,sans-serif;'>"
                + "<table width='100%' cellpadding='0' cellspacing='0' border='0'"
                + " style='background-color:" + C_PAGE_BG + ";'><tr>"
                + "<td align='center' style='padding:40px 16px;'>"
                + "<table width='600' cellpadding='0' cellspacing='0' border='0'"
                + " style='max-width:600px;width:100%;'>"

                // Header
                + "<tr><td style='background-color:" + C_HEADER_BG + ";border-radius:8px 8px 0 0;"
                + "padding:32px;text-align:center;'>"
                + logoHtml
                + "<p style='margin:0;color:#ffffff;font-size:17px;font-weight:700;"
                + "letter-spacing:2px;'>Kelab Fotokreatif (KFK)</p>"
                + "<p style='margin:6px 0 0;color:" + C_HEADER_SUB + ";font-size:12px;"
                + "letter-spacing:3px;text-transform:uppercase;'>" + safeName + "</p>"
                + "</td></tr>"

                // Body card
                + "<tr><td style='background-color:" + C_CARD_BG + ";padding:40px 32px;"
                + "border-radius:0 0 8px 8px;'>"
                + bodyHtml
                + "</td></tr>"

                // Footer
                + "<tr><td style='padding:24px 16px;text-align:center;'>"
                + "<p style='margin:0;color:" + C_SUBTLE + ";font-size:12px;'>"
                + "© 2026 Kelab Fotokreatif (KFK). All rights reserved.</p>"
                + "<p style='margin:4px 0 0;color:" + C_SUBTLE + ";font-size:11px;'>"
                + "Powered by " + safeName + "</p>"
                + "<p style='margin:6px 0 0;color:" + C_SUBTLE + ";font-size:12px;'>"
                + "This is an automated message — please do not reply.</p>"
                + "</td></tr>"

                + "</table></td></tr></table></body></html>";
    }

    private String h2(String text) {
        return "<h2 style='margin:16px 0 8px;color:" + C_FOREGROUND + ";font-size:22px;"
                + "font-weight:700;'>" + esc(text) + "</h2>";
    }

    private String p(String text) {
        return "<p style='margin:16px 0;color:" + C_TEXT + ";font-size:15px;line-height:1.6;'>"
                + esc(text) + "</p>";
    }

    private String note(String text) {
        return "<p style='margin:24px 0 0;color:" + C_SUBTLE + ";font-size:13px;line-height:1.6;"
                + "border-top:1px solid " + C_BORDER + ";padding-top:16px;'>" + esc(text) + "</p>";
    }

    private String badge(String text, String bgColor, String textColor) {
        return "<p style='margin:0 0 16px;'><span style='display:inline-block;"
                + "background-color:" + bgColor + ";color:" + textColor + ";"
                + "font-size:12px;font-weight:700;letter-spacing:1px;text-transform:uppercase;"
                + "padding:4px 12px;border-radius:999px;'>" + esc(text) + "</span></p>";
    }

    private String button(String text, String href, String bgColor) {
        return "<table cellpadding='0' cellspacing='0' border='0' style='margin:28px auto;'><tr>"
                + "<td style='background-color:" + bgColor + ";border-radius:6px;text-align:center;'>"
                + "<a href='" + esc(href) + "' style='display:inline-block;padding:14px 36px;"
                + "color:#ffffff;font-size:15px;font-weight:600;text-decoration:none;"
                + "letter-spacing:0.3px;'>" + esc(text) + "</a>"
                + "</td></tr></table>";
    }

    private String loginButton() {
        return button("Log In to iFoto", loginUrl, C_BRAND);
    }

    private String infoTable(String[][] rows) {
        StringBuilder sb = new StringBuilder();
        sb.append("<table width='100%' cellpadding='0' cellspacing='0' border='0'"
                + " style='margin:20px 0;border-radius:6px;overflow:hidden;"
                + "border:1px solid " + C_BORDER + ";'>");
        for (String[] row : rows) {
            sb.append("<tr>")
                    .append("<td style='padding:12px 16px;background-color:" + C_MUTED_BG + ";"
                            + "color:" + C_MUTED_FG + ";font-size:13px;font-weight:600;text-transform:uppercase;"
                            + "letter-spacing:0.5px;border-bottom:1px solid " + C_BORDER + ";"
                            + "white-space:nowrap;width:38%;'>")
                    .append(esc(row[0])).append("</td>")
                    .append("<td style='padding:12px 16px;background-color:" + C_CARD_BG + ";"
                            + "color:" + C_FOREGROUND + ";font-size:14px;border-bottom:1px solid " + C_BORDER + ";'>")
                    .append(esc(row[1])).append("</td>")
                    .append("</tr>");
        }
        sb.append("</table>");
        return sb.toString();
    }

    // ── Utilities ─────────────────────────────────────────────────────────────

    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter D_FMT  = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private static String fmt(LocalDateTime dt) {
        return dt != null ? dt.format(DT_FMT) : "—";
    }

    private static String fmtDate(LocalDate d) {
        return d != null ? d.format(D_FMT) : "—";
    }

    private static String esc(String text) {
        if (text == null)
            return "";
        return text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#x27;");
    }
}
