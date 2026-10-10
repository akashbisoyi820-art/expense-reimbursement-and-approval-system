package org.smartcoders.expensereimbursementandapprovalsystem.service;

import lombok.RequiredArgsConstructor;
import org.smartcoders.expensereimbursementandapprovalsystem.dto.CommentResponse;
import org.smartcoders.expensereimbursementandapprovalsystem.entity.Comment;
import org.smartcoders.expensereimbursementandapprovalsystem.entity.User;
import org.smartcoders.expensereimbursementandapprovalsystem.repository.CommentRepository;
import org.smartcoders.expensereimbursementandapprovalsystem.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CommentService {

    private final CommentRepository commentRepository;
    private final UserRepository userRepository;
    private final ExpenseService expenseService;

    public List<CommentResponse> getComments(Long expenseId, String email, boolean privileged) {
        expenseService.requireAccessibleExpense(expenseId, email, privileged);
        return commentRepository.findByExpenseIdOrderByCreatedAtAscIdAsc(expenseId)
                .stream()
                .map(c -> toResponse(c, email))
                .toList();
    }

    @Transactional
    public CommentResponse addComment(Long expenseId, String email, boolean privileged, String message) {
        expenseService.requireAccessibleExpense(expenseId, email, privileged);

        User author = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        Comment comment = new Comment();
        comment.setExpenseId(expenseId);
        comment.setAuthorName(author.getName());
        comment.setAuthorEmail(author.getEmail());
        comment.setAuthorRole(author.getRole().name());
        comment.setMessage(message.trim());

        return toResponse(commentRepository.save(comment), email);
    }

    private CommentResponse toResponse(Comment c, String currentEmail) {
        return new CommentResponse(
                c.getId(),
                c.getAuthorName(),
                c.getAuthorRole(),
                c.getMessage(),
                c.getCreatedAt(),
                c.getAuthorEmail().equals(currentEmail)
        );
    }
}