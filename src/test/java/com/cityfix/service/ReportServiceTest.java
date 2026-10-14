package com.cityfix.service;

import com.cityfix.dto.CreateReportRequest;
import com.cityfix.exception.DuplicateUpvoteException;
import com.cityfix.exception.ReportNotFoundException;
import com.cityfix.model.Category;
import com.cityfix.model.Report;
import com.cityfix.model.ReportStatus;
import com.cityfix.model.Upvote;
import com.cityfix.repository.ReportRepository;
import com.cityfix.repository.UpvoteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    @Mock
    private ReportRepository reportRepository;

    @Mock
    private UpvoteRepository upvoteRepository;

    private ReportService reportService;

    @BeforeEach
    void setUp() {
        reportService = new ReportService(reportRepository, upvoteRepository);
    }

    @Test
    void createReportStartsAsReportedWithZeroVotes() {
        CreateReportRequest request = new CreateReportRequest("Pothole", "Deep hole in the road",
                Category.POTHOLE, "Downtown", "Main St near no. 42", "Ana", "ana@example.com");
        when(reportRepository.save(any(Report.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Report report = reportService.createReport(request);

        assertThat(report.getTitle()).isEqualTo("Pothole");
        assertThat(report.getStatus()).isEqualTo(ReportStatus.REPORTED);
        assertThat(report.getUpvoteCount()).isZero();
        assertThat(report.getCreatedAt()).isNotNull();
        assertThat(report.getUpdatedAt()).isNotNull();
    }

    @Test
    void findReportByIdThrowsWhenReportDoesNotExist() {
        when(reportRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reportService.findReportById(99L))
                .isInstanceOf(ReportNotFoundException.class)
                .hasMessage("Report with id 99 not found");
    }

    @Test
    void upvoteReportIncreasesUpvoteCount() {
        Report report = new Report();
        report.setId(1L);
        report.setUpvoteCount(2);
        when(reportRepository.findById(1L)).thenReturn(Optional.of(report));
        when(upvoteRepository.existsByReportIdAndVoterEmail(1L, "ben@example.com")).thenReturn(false);
        when(reportRepository.save(report)).thenReturn(report);

        Report result = reportService.upvoteReport(1L, "ben@example.com");

        assertThat(result.getUpvoteCount()).isEqualTo(3);
        verify(upvoteRepository).save(any(Upvote.class));
    }

    @Test
    void upvoteReportThrowsWhenEmailAlreadyVoted() {
        Report report = new Report();
        report.setId(1L);
        report.setUpvoteCount(2);
        when(reportRepository.findById(1L)).thenReturn(Optional.of(report));
        when(upvoteRepository.existsByReportIdAndVoterEmail(1L, "ben@example.com")).thenReturn(true);

        assertThatThrownBy(() -> reportService.upvoteReport(1L, "ben@example.com"))
                .isInstanceOf(DuplicateUpvoteException.class);

        assertThat(report.getUpvoteCount()).isEqualTo(2);
        verify(upvoteRepository, never()).save(any(Upvote.class));
    }

    @Test
    void updateStatusToFixedSetsFixedAt() {
        Report report = new Report();
        report.setId(1L);
        report.setStatus(ReportStatus.IN_PROGRESS);
        when(reportRepository.findById(1L)).thenReturn(Optional.of(report));
        when(reportRepository.save(report)).thenReturn(report);

        Report result = reportService.updateStatus(1L, ReportStatus.FIXED);

        assertThat(result.getStatus()).isEqualTo(ReportStatus.FIXED);
        assertThat(result.getFixedAt()).isNotNull();
        assertThat(result.getFixedAt()).isEqualTo(result.getUpdatedAt());
    }

    @Test
    void updateStatusFromFixedToInProgressClearsFixedAt() {
        Report report = new Report();
        report.setId(1L);
        report.setStatus(ReportStatus.FIXED);
        report.setFixedAt(LocalDateTime.of(2026, 1, 10, 9, 0));
        when(reportRepository.findById(1L)).thenReturn(Optional.of(report));
        when(reportRepository.save(report)).thenReturn(report);

        Report result = reportService.updateStatus(1L, ReportStatus.IN_PROGRESS);

        assertThat(result.getStatus()).isEqualTo(ReportStatus.IN_PROGRESS);
        assertThat(result.getFixedAt()).isNull();
    }

    @Test
    void updateStatusFromFixedToFixedKeepsOriginalFixedAt() {
        LocalDateTime originalFixedAt = LocalDateTime.of(2026, 1, 10, 9, 0);
        Report report = new Report();
        report.setId(1L);
        report.setStatus(ReportStatus.FIXED);
        report.setFixedAt(originalFixedAt);
        when(reportRepository.findById(1L)).thenReturn(Optional.of(report));

        Report result = reportService.updateStatus(1L, ReportStatus.FIXED);

        assertThat(result.getFixedAt()).isEqualTo(originalFixedAt);
        verify(reportRepository, never()).save(any(Report.class));
    }

    @Test
    void updateStatusToSameStatusLeavesUpdatedAtUnchanged() {
        LocalDateTime originalUpdatedAt = LocalDateTime.of(2026, 1, 5, 12, 0);
        Report report = new Report();
        report.setId(1L);
        report.setStatus(ReportStatus.IN_PROGRESS);
        report.setUpdatedAt(originalUpdatedAt);
        when(reportRepository.findById(1L)).thenReturn(Optional.of(report));

        Report result = reportService.updateStatus(1L, ReportStatus.IN_PROGRESS);

        assertThat(result).isSameAs(report);
        assertThat(result.getUpdatedAt()).isEqualTo(originalUpdatedAt);
        verify(reportRepository).findById(1L);
        verifyNoMoreInteractions(reportRepository);
    }
}
