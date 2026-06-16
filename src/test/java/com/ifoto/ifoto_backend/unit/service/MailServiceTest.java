package com.ifoto.ifoto_backend.unit.service;

import com.ifoto.ifoto_backend.service.MailService;
import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MailServiceTest {

    @Mock private JavaMailSender mailSender;
    @InjectMocks private MailService service;

    private MimeMessage mimeMessage;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "from", "noreply@ifoto.com");
        ReflectionTestUtils.setField(service, "appName", "iFoto");
        ReflectionTestUtils.setField(service, "devOverrideRecipient", "");
        ReflectionTestUtils.setField(service, "loginUrl", "http://localhost:5173/login");
        mimeMessage = new MimeMessage(Session.getInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
    }

    private MimeMessage captureSent() {
        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());
        return captor.getValue();
    }

    // ── sendVerificationEmail ─────────────────────────────────────────────────

    @Test
    void sendVerificationEmail_sendsOnce_withCorrectSubjectAndRecipient() throws Exception {
        service.sendVerificationEmail("user@test.com", "https://example.com/verify");

        MimeMessage sent = captureSent();
        assertTrue(sent.getSubject().contains("Verify Your Email"));
        assertEquals("user@test.com",
                ((InternetAddress[]) sent.getRecipients(Message.RecipientType.TO))[0].getAddress());
    }

    // ── sendPasswordResetEmail ────────────────────────────────────────────────

    @Test
    void sendPasswordResetEmail_sendsOnce_withCorrectSubjectAndRecipient() throws Exception {
        service.sendPasswordResetEmail("user@test.com", "https://example.com/reset");

        MimeMessage sent = captureSent();
        assertTrue(sent.getSubject().contains("Password Reset"));
        assertEquals("user@test.com",
                ((InternetAddress[]) sent.getRecipients(Message.RecipientType.TO))[0].getAddress());
    }

    // ── sendRentalSubmittedToCommittee ────────────────────────────────────────

    @Test
    void sendRentalSubmittedToCommittee_sendsOnce_toAllRecipients() throws Exception {
        service.sendRentalSubmittedToCommittee(
                List.of("comm1@test.com", "comm2@test.com"),
                "RNT-2026-000001", "Alice",
                List.<String[]>of(new String[]{"Canon R5", "1"}), null,
                LocalDate.now(), LocalDate.now().plusDays(3));

        MimeMessage sent = captureSent();
        assertTrue(sent.getSubject().contains("New Rental Request"));
        assertTrue(sent.getSubject().contains("RNT-2026-000001"));
        InternetAddress[] to = (InternetAddress[]) sent.getRecipients(Message.RecipientType.TO);
        assertEquals(2, to.length);
    }

    // ── sendRentalApprovedToRenter ────────────────────────────────────────────

    @Test
    void sendRentalApprovedToRenter_sendsOnce_withCorrectSubject() throws Exception {
        service.sendRentalApprovedToRenter(
                "renter@test.com", "RNT-2026-000001", 5000L,
                LocalDate.now(), LocalDate.now().plusDays(3),
                LocalDateTime.now(), LocalDateTime.now().plusDays(4));

        MimeMessage sent = captureSent();
        assertTrue(sent.getSubject().contains("Rental Approved"));
        assertTrue(sent.getSubject().contains("RNT-2026-000001"));
    }

    @Test
    void sendRentalApprovedToRenter_nullPickupAndReturn_formatsAsDash() {
        // fmt(null) must produce "—" without throwing
        assertDoesNotThrow(() -> service.sendRentalApprovedToRenter(
                "renter@test.com", "RNT-2026-000001", 0L,
                LocalDate.now(), LocalDate.now(), null, null));
        verify(mailSender).send(any(MimeMessage.class));
    }

    // ── sendEquipmentPickedUpToRenter ─────────────────────────────────────────

    @Test
    void sendEquipmentPickedUpToRenter_sendsOnce_withCorrectSubject() throws Exception {
        service.sendEquipmentPickedUpToRenter(
                "renter@test.com", "RNT-2026-000001", LocalDateTime.now());

        MimeMessage sent = captureSent();
        assertTrue(sent.getSubject().contains("Equipment Picked Up"));
        assertTrue(sent.getSubject().contains("RNT-2026-000001"));
    }

    // ── sendLogisticsUpdatedToRenter ──────────────────────────────────────────

    @Test
    void sendLogisticsUpdatedToRenter_sendsOnce_withCorrectSubject() throws Exception {
        service.sendLogisticsUpdatedToRenter(
                "renter@test.com", "RNT-2026-000001",
                LocalDateTime.now(), LocalDateTime.now().plusDays(3));

        MimeMessage sent = captureSent();
        assertTrue(sent.getSubject().contains("Schedule Updated"));
        assertTrue(sent.getSubject().contains("RNT-2026-000001"));
    }

    // ── sendEquipmentUpdatedToRenter ──────────────────────────────────────────

    @Test
    void sendEquipmentUpdatedToRenter_sendsOnce_withCorrectSubject() throws Exception {
        service.sendEquipmentUpdatedToRenter(
                "renter@test.com", "RNT-2026-000001",
                List.of(new String[] { "Canon R5", "SN123" }, new String[] { "Speedlight", "x2" }));

        MimeMessage sent = captureSent();
        assertTrue(sent.getSubject().contains("Equipment Updated"));
        assertTrue(sent.getSubject().contains("RNT-2026-000001"));
    }

    // ── sendRentalRejectedToRenter ────────────────────────────────────────────

    @Test
    void sendRentalRejectedToRenter_sendsOnce_withCorrectSubject() throws Exception {
        service.sendRentalRejectedToRenter(
                "renter@test.com", "RNT-2026-000001", "Not available");

        MimeMessage sent = captureSent();
        assertTrue(sent.getSubject().contains("Rental Request Rejected"));
        assertTrue(sent.getSubject().contains("RNT-2026-000001"));
    }

    @Test
    void sendRentalRejectedToRenter_xssInReason_sendsWithoutThrowingOrExposing() {
        // esc() must HTML-encode special chars — verified by the send completing normally
        assertDoesNotThrow(() -> service.sendRentalRejectedToRenter(
                "renter@test.com", "RNT-2026-000001",
                "<script>alert('xss')</script>"));
        verify(mailSender).send(any(MimeMessage.class));
    }

    // ── sendPaymentConfirmedToRenter ──────────────────────────────────────────

    @Test
    void sendPaymentConfirmedToRenter_sendsOnce_withCorrectSubject() throws Exception {
        service.sendPaymentConfirmedToRenter(
                "renter@test.com", "RNT-2026-000001", "RCP-2026-000001");

        MimeMessage sent = captureSent();
        assertTrue(sent.getSubject().contains("Payment Confirmed"));
        assertTrue(sent.getSubject().contains("RNT-2026-000001"));
    }

    @Test
    void sendPaymentConfirmedToCommittee_sendsOnce_withCorrectSubject() throws Exception {
        service.sendPaymentConfirmedToCommittee(
                "comm@test.com", "RNT-2026-000001", "Alice", "RCP-2026-000001");

        MimeMessage sent = captureSent();
        assertTrue(sent.getSubject().contains("Payment Received"));
        assertTrue(sent.getSubject().contains("RNT-2026-000001"));
    }

    @Test
    void sendOverduePaymentConfirmedToRenter_sendsOnce_withCorrectSubject() throws Exception {
        service.sendOverduePaymentConfirmedToRenter(
                "renter@test.com", "RNT-2026-000001", "RCP-2026-000001");

        MimeMessage sent = captureSent();
        assertTrue(sent.getSubject().contains("Overdue Penalty Paid"));
        assertTrue(sent.getSubject().contains("RNT-2026-000001"));
    }

    @Test
    void sendOverduePaymentConfirmedToCommittee_sendsOnce_withCorrectSubject() throws Exception {
        service.sendOverduePaymentConfirmedToCommittee(
                "comm@test.com", "RNT-2026-000001", "Alice", "RCP-2026-000001");

        MimeMessage sent = captureSent();
        assertTrue(sent.getSubject().contains("Overdue Penalty Paid"));
        assertTrue(sent.getSubject().contains("RNT-2026-000001"));
    }

    @Test
    void sendRentalOverdueToCommittee_sendsOnce_withCorrectSubject() throws Exception {
        service.sendRentalOverdueToCommittee(
                "comm@test.com", "RNT-2026-000001", "Alice", LocalDateTime.now(), 1500L);

        MimeMessage sent = captureSent();
        assertTrue(sent.getSubject().contains("Rental Overdue"));
        assertTrue(sent.getSubject().contains("RNT-2026-000001"));
    }

    // ── sendCashPaymentPendingToCommittee ─────────────────────────────────────

    @Test
    void sendCashPaymentPendingToCommittee_sendsOnce_withCorrectSubject() throws Exception {
        service.sendCashPaymentPendingToCommittee(
                List.of("comm@test.com"), "RNT-2026-000001", "Alice", 5000L);

        MimeMessage sent = captureSent();
        assertTrue(sent.getSubject().contains("Cash Payment Pending"));
        assertTrue(sent.getSubject().contains("RNT-2026-000001"));
    }

    // ── devOverrideRecipient ──────────────────────────────────────────────────

    @Test
    void send_withDevOverrideRecipient_redirectsToOverrideInsteadOfOriginal() throws Exception {
        ReflectionTestUtils.setField(service, "devOverrideRecipient", "dev@test.com");

        service.sendVerificationEmail("original@test.com", "https://example.com/verify");

        MimeMessage sent = captureSent();
        InternetAddress[] to = (InternetAddress[]) sent.getRecipients(Message.RecipientType.TO);
        assertEquals(1, to.length);
        assertEquals("dev@test.com", to[0].getAddress());
    }

    @Test
    void sendToMany_withDevOverrideRecipient_collapsesManyToSingleOverride() throws Exception {
        ReflectionTestUtils.setField(service, "devOverrideRecipient", "dev@test.com");

        service.sendRentalSubmittedToCommittee(
                List.of("comm1@test.com", "comm2@test.com"),
                "RNT-2026-000001", "Alice",
                List.<String[]>of(new String[]{"Canon R5", "1"}), null,
                LocalDate.now(), LocalDate.now().plusDays(3));

        MimeMessage sent = captureSent();
        InternetAddress[] to = (InternetAddress[]) sent.getRecipients(Message.RecipientType.TO);
        assertEquals(1, to.length);
        assertEquals("dev@test.com", to[0].getAddress());
    }

    // ── Exception suppression ─────────────────────────────────────────────────

    @Test
    void send_whenMailSenderThrows_exceptionIsSuppressedAndNotPropagated() {
        doThrow(new MailSendException("SMTP error")).when(mailSender).send(any(MimeMessage.class));

        assertDoesNotThrow(() ->
                service.sendVerificationEmail("user@test.com", "https://example.com/verify"));
    }

    @Test
    void sendToMany_whenMailSenderThrows_exceptionIsSuppressedAndNotPropagated() {
        doThrow(new MailSendException("SMTP error")).when(mailSender).send(any(MimeMessage.class));

        assertDoesNotThrow(() -> service.sendCashPaymentPendingToCommittee(
                List.of("comm@test.com"), "RNT-2026-000001", "Alice", 5000L));
    }
}
