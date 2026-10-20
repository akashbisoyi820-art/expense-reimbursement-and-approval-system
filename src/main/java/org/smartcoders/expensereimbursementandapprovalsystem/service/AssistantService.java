package org.smartcoders.expensereimbursementandapprovalsystem.service;

import lombok.RequiredArgsConstructor;
import org.smartcoders.expensereimbursementandapprovalsystem.dto.AssistantResponse;
import org.smartcoders.expensereimbursementandapprovalsystem.dto.ExpenseResponse;
import org.smartcoders.expensereimbursementandapprovalsystem.dto.PaymentResponse;
import org.smartcoders.expensereimbursementandapprovalsystem.entity.Expense;
import org.smartcoders.expensereimbursementandapprovalsystem.entity.ExpenseStatus;
import org.smartcoders.expensereimbursementandapprovalsystem.entity.PolicyRule;
import org.smartcoders.expensereimbursementandapprovalsystem.entity.User;
import org.smartcoders.expensereimbursementandapprovalsystem.repository.ExpenseRepository;
import org.smartcoders.expensereimbursementandapprovalsystem.repository.PolicyRuleRepository;
import org.smartcoders.expensereimbursementandapprovalsystem.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// A rule-based assistant: it matches keywords in the question and answers from the real
// policy and the user's own claims. It does not call any outside service.
@Service
@RequiredArgsConstructor
public class AssistantService {

    private static final Locale INDIA = new Locale("en", "IN");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);

    // Order matters: when two topics score the same, the earlier one wins
    private enum Topic {
        POLICY_EDIT, QUEUE, REJECTED, RECEIPT, SUBMIT, PROCESS, PAYMENT, CURRENCY,
        LIMITS, MY_CLAIMS, HISTORY, CHARTS, SEARCH, HELP
    }

    private static final Map<Topic, List<String>> KEYWORDS = new EnumMap<>(Topic.class);

    static {
        KEYWORDS.put(Topic.POLICY_EDIT, List.of("change policy", "edit policy", "update policy",
                "modify policy", "change the policy", "change limit", "change the limit",
                "update limit", "set limit", "set the limit", "edit limit", "who can change",
                "policy page", "policy settings"));
        KEYWORDS.put(Topic.QUEUE, List.of("pending approval", "pending approvals", "to approve",
                "waiting for approval", "waiting for my", "approval queue", "needs approval",
                "need approval", "awaiting approval", "awaiting"));
        KEYWORDS.put(Topic.REJECTED, List.of("reject", "declined", "denied", "not approved",
                "why was", "why is my", "kyun reject", "reject kyun", "reject kyu"));
        KEYWORDS.put(Topic.RECEIPT, List.of("receipt", "bill", "invoice", "proof", "rasid"));
        KEYWORDS.put(Topic.SUBMIT, List.of("submit", "create a claim", "new claim", "new expense",
                "add expense", "add a claim", "raise a claim", "file a claim", "how to claim",
                "how do i claim", "claim kaise", "expense kaise", "kaise submit", "claim karna",
                "claim kare"));
        KEYWORDS.put(Topic.PROCESS, List.of("process", "workflow", "how long", "how does",
                "what happens", "kab tak", "kitna time", "stages", "who approves",
                "who can approve", "who reimburses", "who pays", "next step"));
        KEYWORDS.put(Topic.PAYMENT, List.of("payment", "paid", "pay me", "get paid", "money",
                "salary", "bank", "upi", "utr", "cheque", "cash", "refund", "paisa", "paise",
                "kab milega", "when will i get"));
        KEYWORDS.put(Topic.CURRENCY, List.of("currency", "currencies", "usd", "eur", "gbp", "aed",
                "sgd", "aud", "cad", "jpy", "dollar", "euro", "pound", "dirham", "exchange rate",
                "foreign", "convert", "conversion"));
        KEYWORDS.put(Topic.LIMITS, List.of("limit", "maximum", "max", "cap", "up to", "upto",
                "how much", "allowed", "kitna", "kitni", "seema", "policy", "rules", "rule"));
        KEYWORDS.put(Topic.MY_CLAIMS, List.of("my claim", "my expense", "my total", "show claims",
                "show my", "how many", "total claimed", "pending", "approved", "reimbursed",
                "meri claim", "mere claim", "kitne claim", "summary", "overview", "status"));
        KEYWORDS.put(Topic.HISTORY, List.of("history", "audit", "timeline", "who approved",
                "who rejected", "track", "comment", "discuss", "chat with", "message the manager",
                "ask the manager"));
        KEYWORDS.put(Topic.CHARTS, List.of("chart", "graph", "analytics", "report",
                "spending overview", "department wise", "month wise", "category wise",
                "statistics", "insights"));
        KEYWORDS.put(Topic.SEARCH, List.of("search", "filter", "find a claim", "sort",
                "pagination", "look for"));
        KEYWORDS.put(Topic.HELP, List.of("help", "what can you do", "what can i ask", "options",
                "menu", "madad", "commands"));
    }

    private static final Set<String> GREETING_WORDS = Set.of("hi", "hii", "hello", "hey", "hola",
            "namaste", "good", "morning", "afternoon", "evening", "there", "assistant", "bot");

    private static final Set<String> THANKS_WORDS = Set.of("thanks", "thank", "thx", "ty", "you",
            "so", "much", "a", "lot", "shukriya", "dhanyavad", "okay", "ok", "great", "awesome",
            "cool", "nice", "perfect", "got", "it", "for", "the", "help");

    // Extra words people use for each category
    private static final Map<String, List<String>> CATEGORY_WORDS = Map.of(
            "travel", List.of("travel", "cab", "taxi", "flight", "train", "bus", "uber", "ola",
                    "fuel", "petrol", "toll", "airport"),
            "food", List.of("food", "meal", "lunch", "dinner", "breakfast", "snack",
                    "restaurant", "coffee"),
            "accommodation", List.of("accommodation", "hotel", "stay", "lodging", "airbnb"),
            "office supplies", List.of("supplies", "stationery", "printer", "cartridge",
                    "cartridges"),
            "other", List.of("misc", "miscellaneous"));

    private static final Pattern HASH_ID = Pattern.compile("#\\s*(\\d{1,9})\\b");
    private static final Pattern EXPLICIT_ID =
            Pattern.compile("\\b(?:claim|expense)\\s*(?:id|no|number)\\s*#?\\s*(\\d{1,9})\\b");
    private static final Pattern PLAIN_ID = Pattern.compile("\\b(?:claim|expense)\\s*(\\d{1,9})\\b");
    private static final Pattern AMOUNT =
            Pattern.compile("\\b(\\d+(?:\\.\\d+)?)\\s*(k|thousand|lakh|lakhs|lac)?\\b");

    private record Context(User user, boolean privileged, boolean admin) {
        String firstName() {
            String name = user.getName() == null ? "" : user.getName().trim();
            int space = name.indexOf(' ');
            return space > 0 ? name.substring(0, space) : name;
        }
    }

    private final PolicyRuleRepository ruleRepository;
    private final ExpenseRepository expenseRepository;
    private final UserRepository userRepository;
    private final ExpenseService expenseService;
    private final ExchangeRateService exchangeRateService;

    @Transactional(readOnly = true)
    public AssistantResponse ask(String email, boolean privileged, boolean admin, String message) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        Context ctx = new Context(user, privileged, admin);

        String text = normalize(message);
        if (text.isEmpty()) {
            return help(ctx);
        }

        String[] tokens = text.split(" ");
        if (allIn(tokens, GREETING_WORDS)) {
            return greeting(ctx);
        }
        if (allIn(tokens, THANKS_WORDS)) {
            return thanks(ctx);
        }

        List<PolicyRule> rules = ruleRepository.findAllByOrderByIdAsc();
        PolicyRule category = findCategory(text, rules);

        // "status of claim #12"
        Long claimId = findClaimId(text, category != null, tokens.length);
        if (claimId != null) {
            return claimStatus(ctx, claimId);
        }

        // "can I claim 7500 for travel?"
        BigDecimal amount = findAmount(text);
        if (category != null && amount != null) {
            return checkAmount(ctx, category, amount);
        }

        // "how do I approve a claim?"
        if (containsAny(text, "approve", "reject", "reimburse")
                && containsAny(text, "how", "steps", "kaise")) {
            return approveHow(ctx);
        }

        Topic best = null;
        int bestScore = 0;
        for (Map.Entry<Topic, List<String>> entry : KEYWORDS.entrySet()) {
            int value = score(text, entry.getValue());
            if (value > bestScore) {
                best = entry.getKey();
                bestScore = value;
            }
        }
        if (best == null) {
            return unknown(ctx);
        }

        return switch (best) {
            case POLICY_EDIT -> policyEdit(ctx);
            case QUEUE -> queue(ctx);
            case REJECTED -> rejected(ctx);
            case RECEIPT -> receipt(ctx, text, category, rules);
            case SUBMIT -> submit(ctx);
            case PROCESS -> process(ctx);
            case PAYMENT -> payment(ctx);
            case CURRENCY -> currency(ctx);
            case LIMITS -> limits(ctx, category, rules);
            case MY_CLAIMS -> myClaims(ctx, text);
            case HISTORY -> history(ctx);
            case CHARTS -> charts(ctx);
            case SEARCH -> search(ctx);
            case HELP -> help(ctx);
        };
    }

    // ---------- Answers ----------

    private AssistantResponse greeting(Context ctx) {
        return reply("Hi " + ctx.firstName() + "! I am your expense assistant. "
                + "I can explain the reimbursement policy, how to submit a claim, and check your claims.\n\n"
                + "What would you like to know?", defaultSuggestions(ctx));
    }

    private AssistantResponse thanks(Context ctx) {
        return reply("You are welcome! Ask me anything else about claims or the policy.",
                defaultSuggestions(ctx));
    }

    private AssistantResponse help(Context ctx) {
        List<String> lines = new ArrayList<>();
        lines.add("Hi " + ctx.firstName() + "! I can help with:");
        lines.add("• Policy limits, for example \"What is the Travel limit?\"");
        lines.add("• Whether a claim is fine, for example \"Can I claim 7500 for travel?\"");
        lines.add("• Receipt rules");
        lines.add("• How to submit a claim and how reimbursement works");
        lines.add("• Your claims: \"Show my claims\", \"Status of claim #12\", \"Why was my claim rejected?\"");
        lines.add("• Currencies, payments, history, charts and search");
        if (ctx.privileged()) {
            lines.add("• Pending approvals and how to approve or reject a claim");
        }
        if (ctx.admin()) {
            lines.add("• How to change the policy");
        }
        return reply(String.join("\n", lines), defaultSuggestions(ctx));
    }

    private AssistantResponse unknown(Context ctx) {
        return reply("I am not sure I understood that, and I would rather not guess.\n\n"
                + "I can answer questions about the policy limits, receipts, submitting a claim, "
                + "how reimbursement works, currencies, payments, and the status of your claims. "
                + "Try one of these:", defaultSuggestions(ctx));
    }

    private AssistantResponse submit(Context ctx) {
        return reply("To submit a claim:\n"
                        + "1. Open your Dashboard and find \"Submit New Expense\".\n"
                        + "2. Choose the category, the currency and the amount.\n"
                        + "3. Pick the expense date and add a short description.\n"
                        + "4. Attach a receipt (PDF, PNG or JPG, up to 5 MB). It is compulsory above the "
                        + "policy limit for the category.\n"
                        + "5. Press Submit. Your manager will review it.\n\n"
                        + "The form shows the policy limits for the category and the exchange rate for foreign currencies.",
                List.of("When do I need a receipt?", "What are the policy limits?",
                        "How does reimbursement work?", "Which currencies can I use?"));
    }

    private AssistantResponse process(Context ctx) {
        return reply("How a claim moves:\n"
                        + "1. You submit the claim (status Pending). The policy is checked and any warnings are saved.\n"
                        + "2. A manager reviews it (receipt, amount, warnings) and approves or rejects it. "
                        + "A rejection always has a reason.\n"
                        + "3. Once approved, an admin reimburses it and records the payment: method, reference number and date.\n"
                        + "4. You see every change on your Dashboard. The Details panel shows the full timeline and the payment.\n\n"
                        + "The app records the payment. The money itself is paid outside the app.",
                List.of("How do I submit a claim?", "When will I get paid?",
                        "Why was my claim rejected?", "Show my claims"));
    }

    private AssistantResponse payment(Context ctx) {
        return reply("After a manager approves your claim, an admin reimburses it and records how it was paid: "
                        + "the method (bank transfer, UPI, cash or cheque), the reference number and the date.\n\n"
                        + "You can see this in the claim's Details panel once the status is Reimbursed. "
                        + "The app does not move money itself. The actual transfer happens through your company's usual payment process.",
                List.of("How does reimbursement work?", "Show my claims", "What are the policy limits?"));
    }

    private AssistantResponse currency(Context ctx) {
        List<String> codes = exchangeRateService.getCurrencies().stream()
                .map(c -> c.code() + " (" + c.name() + ")")
                .toList();
        return reply("You can submit claims in these currencies:\n• " + String.join("\n• ", codes) + "\n\n"
                        + "Choose the currency in the claim form. The form shows today's exchange rate and the amount in INR. "
                        + "That rate is saved with the claim, so later rate changes do not alter an old claim. "
                        + "Policy limits, totals and charts all use the INR amount.",
                List.of("How do I submit a claim?", "What are the policy limits?",
                        "When do I need a receipt?"));
    }

    private AssistantResponse history(Context ctx) {
        return reply("Open any claim with the Details button. The panel has two tabs:\n"
                        + "• Timeline: who submitted, approved, rejected or reimbursed the claim, and when, with the reason or payment details.\n"
                        + "• Comments: a chat on that claim between the employee and the managers or admins.\n\n"
                        + "Only the claim's owner, managers and admins can see a claim's timeline and comments.",
                List.of("How does reimbursement work?", "Show my claims"));
    }

    private AssistantResponse charts(Context ctx) {
        return reply("The Spending Overview card shows your spending in INR by category, status and month"
                        + (ctx.privileged() ? ", and by department" : "") + ". "
                        + "Use the quick ranges (This month, Last 3 months, This year), the status box or custom dates.\n\n"
                        + "Click a slice or a bar to see the claims behind it in the table below.",
                List.of("How do I search for a claim?", "Show my claims"));
    }

    private AssistantResponse search(Context ctx) {
        return reply("In the claims table, type in the search box (description, category"
                        + (ctx.privileged() ? " or employee" : "") + ") and press Search. "
                        + "Press More filters for category, "
                        + (ctx.privileged() ? "department, employee, " : "")
                        + "dates and an amount range (in INR). The status buttons filter by status, "
                        + "and the column headings sort the table.",
                List.of("Show my claims", "How do charts work?"));
    }

    private AssistantResponse policyEdit(Context ctx) {
        if (!ctx.admin()) {
            return reply("Only admins can change the policy. You can ask me what the current rules are, "
                            + "for example \"What is the Travel limit?\".",
                    List.of("What are the policy limits?", "When do I need a receipt?"));
        }
        return reply("To change the policy:\n"
                        + "1. Open the Approvals page.\n"
                        + "2. Press the Policy button in the header.\n"
                        + "3. Edit the limits of a category. Leave a box empty to remove that rule.\n"
                        + "4. Press Save changes.\n\n"
                        + "Every change is saved in the policy history with your name and the time. "
                        + "Changes apply to claims submitted after saving.",
                List.of("What are the policy limits?", "Show pending approvals"));
    }

    private AssistantResponse approveHow(Context ctx) {
        if (!ctx.privileged()) {
            return reply("Claims are approved by managers and admins. You cannot approve your own claim. "
                            + "Your Dashboard shows the status of each claim, and the Details panel lets you comment on it.",
                    List.of("How does reimbursement work?", "Show my claims"));
        }
        List<String> lines = new ArrayList<>();
        lines.add("To handle claims:");
        lines.add("1. Open the Approvals page.");
        lines.add("2. In Pending Expenses press Details to see the policy warnings, comments and the receipt.");
        lines.add("3. Press Approve, or Reject and write the reason. If the policy requires a receipt that has not been uploaded, Approve stays disabled.");
        if (ctx.admin()) {
            lines.add("4. In Approved Expenses press Reimburse, enter the payment method, reference number and date, and confirm.");
        }
        return reply(String.join("\n", lines),
                List.of("Show pending approvals", "What are the policy limits?"));
    }

    private AssistantResponse limits(Context ctx, PolicyRule category, List<PolicyRule> rules) {
        if (rules.isEmpty()) {
            return reply("No policy rules have been set yet.", defaultSuggestions(ctx));
        }
        if (category != null) {
            return reply(category.getCategory() + " policy (all amounts in INR):\n" + ruleLines(category)
                            + "\n\nA claim over a limit is still submitted, but your manager sees an Over limit warning.",
                    List.of("Can I claim 7500 for " + category.getCategory() + "?",
                            "When do I need a receipt?", "How do I submit a claim?"));
        }

        List<String> lines = new ArrayList<>();
        lines.add("Current policy (all amounts in INR):");
        for (PolicyRule r : rules) {
            lines.add("• " + r.getCategory() + ": "
                    + (r.getMaxPerClaim() == null ? "no per-claim limit" : money(r.getMaxPerClaim()) + " per claim")
                    + ", "
                    + (r.getMonthlyLimit() == null ? "no monthly limit" : money(r.getMonthlyLimit()) + " per month")
                    + ", receipt " + receiptText(r));
        }
        lines.add("");
        lines.add("A claim over a limit is still submitted, but your manager sees an Over limit warning.");
        return reply(String.join("\n", lines),
                List.of("What is the Travel limit?", "Can I claim 7500 for travel?",
                        "When do I need a receipt?"));
    }

    private AssistantResponse receipt(Context ctx, String text, PolicyRule category, List<PolicyRule> rules) {
        List<String> lines = new ArrayList<>();

        if (containsAny(text, "upload", "attach", "add", "how")) {
            lines.add("To add a receipt:");
            lines.add("• While submitting, choose a PDF, PNG or JPG (up to 5 MB) in the form.");
            lines.add("• Or, for a pending claim that has none, use the file box in the Receipt column of My Expenses.");
            lines.add("A receipt can be added only while the claim is pending, and only once.");
            lines.add("");
        }

        if (category != null) {
            lines.add(category.getCategory() + ": receipt " + receiptText(category) + ".");
        } else if (!rules.isEmpty()) {
            lines.add("Receipt rules (INR):");
            for (PolicyRule r : rules) {
                lines.add("• " + r.getCategory() + ": " + receiptText(r));
            }
        }
        lines.add("");
        lines.add("Where a receipt is compulsory you cannot submit without one, and a manager cannot approve until it is uploaded.");

        return reply(String.join("\n", lines).trim(),
                List.of("What are the policy limits?", "How do I submit a claim?"));
    }

    private AssistantResponse myClaims(Context ctx, String text) {
        List<Expense> mine = expenseRepository.findByUserId(ctx.user().getId());
        if (mine.isEmpty()) {
            return reply("You have not submitted any claims yet. Use \"Submit New Expense\" on your Dashboard to start.",
                    List.of("How do I submit a claim?", "What are the policy limits?"));
        }

        ExpenseStatus filter = statusIn(text);
        if (filter != null) {
            List<Expense> matching = mine.stream()
                    .filter(e -> e.getStatus() == filter)
                    .sorted(Comparator.comparing(Expense::getId).reversed())
                    .toList();
            if (matching.isEmpty()) {
                return reply("You have no " + filter.name().toLowerCase(Locale.ROOT) + " claims.",
                        List.of("Show my claims", "How does reimbursement work?"));
            }
            List<String> lines = new ArrayList<>();
            lines.add("You have " + matching.size() + " " + filter.name().toLowerCase(Locale.ROOT)
                    + (matching.size() == 1 ? " claim" : " claims") + " (total "
                    + money(sum(matching)) + "):");
            matching.stream().limit(5).forEach(e -> lines.add(claimLine(e)));
            if (matching.size() > 5) {
                lines.add("...and " + (matching.size() - 5) + " more. Use the search table on your Dashboard to see all.");
            }
            return reply(String.join("\n", lines),
                    List.of("Show my claims", "Why was my claim rejected?"));
        }

        List<String> lines = new ArrayList<>();
        lines.add("You have " + mine.size() + (mine.size() == 1 ? " claim" : " claims")
                + ", worth " + money(sum(mine)) + " in total:");
        for (ExpenseStatus status : ExpenseStatus.values()) {
            List<Expense> group = mine.stream().filter(e -> e.getStatus() == status).toList();
            if (!group.isEmpty()) {
                lines.add("• " + capitalize(status.name()) + ": " + group.size()
                        + " (" + money(sum(group)) + ")");
            }
        }

        long needReceipt = mine.stream()
                .filter(e -> e.getStatus() == ExpenseStatus.PENDING)
                .filter(e -> Boolean.TRUE.equals(e.getReceiptRequired()) && !hasReceipt(e))
                .count();
        if (needReceipt > 0) {
            lines.add("");
            lines.add("Action needed: " + needReceipt + " pending "
                    + (needReceipt == 1 ? "claim needs" : "claims need")
                    + " a receipt before it can be approved.");
        }
        return reply(String.join("\n", lines),
                List.of("Show my pending claims", "Why was my claim rejected?",
                        "How does reimbursement work?"));
    }

    private AssistantResponse rejected(Context ctx) {
        List<Expense> rejected = expenseRepository.findByUserId(ctx.user().getId()).stream()
                .filter(e -> e.getStatus() == ExpenseStatus.REJECTED)
                .sorted(Comparator.comparing(Expense::getId).reversed())
                .toList();

        if (rejected.isEmpty()) {
            return reply("You have no rejected claims. If a claim is rejected, the manager's reason is shown "
                            + "on your Dashboard and in the claim's Details.",
                    List.of("Show my claims", "How does reimbursement work?"));
        }

        List<String> lines = new ArrayList<>();
        lines.add("Your rejected " + (rejected.size() == 1 ? "claim:" : "claims:"));
        for (Expense e : rejected.stream().limit(5).toList()) {
            lines.add(claimLine(e));
            lines.add("   Reason: " + (e.getRejectionReason() == null ? "not given" : e.getRejectionReason()));
        }
        lines.add("");
        lines.add("You can fix the issue and submit a new claim. Use the Comments tab in a claim's Details to ask your manager.");
        return reply(String.join("\n", lines),
                List.of("How do I submit a claim?", "When do I need a receipt?"));
    }

    private AssistantResponse queue(Context ctx) {
        if (!ctx.privileged()) {
            return reply("Approvals are handled by managers and admins. Here is what I can show for you instead: "
                            + "ask \"Show my pending claims\" to see your claims that are waiting for a decision.",
                    List.of("Show my pending claims", "How does reimbursement work?"));
        }

        List<Expense> pending = expenseRepository.findByStatus(ExpenseStatus.PENDING).stream()
                .sorted(Comparator.comparing(Expense::getId))
                .toList();
        if (pending.isEmpty()) {
            return reply("There are no claims waiting for approval right now.",
                    List.of("How do I approve a claim?", "What are the policy limits?"));
        }

        long flagged = pending.stream()
                .filter(e -> e.getPolicyFlags() != null && !e.getPolicyFlags().isBlank()).count();
        long missing = pending.stream()
                .filter(e -> Boolean.TRUE.equals(e.getReceiptRequired()) && !hasReceipt(e)).count();

        List<String> lines = new ArrayList<>();
        lines.add(pending.size() + (pending.size() == 1 ? " claim is" : " claims are")
                + " waiting for approval, worth " + money(sum(pending)) + ".");
        if (flagged > 0) {
            lines.add("• " + flagged + " over the policy limit (look for the Over limit chip)");
        }
        if (missing > 0) {
            lines.add("• " + missing + " still need a receipt, so they cannot be approved yet");
        }
        lines.add("");
        lines.add("Oldest first:");
        for (Expense e : pending.stream().limit(5).toList()) {
            lines.add("• #" + e.getId() + " " + e.getUser().getName() + ", " + e.getCategory() + " "
                    + money(inBase(e)) + (hasFlag(e) ? " (over limit)" : ""));
        }
        return reply(String.join("\n", lines),
                List.of("How do I approve a claim?", "What are the policy limits?"));
    }

    private AssistantResponse claimStatus(Context ctx, long id) {
        Expense expense;
        try {
            expense = expenseService.requireAccessibleExpense(id, ctx.user().getEmail(), ctx.privileged());
        } catch (ResponseStatusException e) {
            // The same message for "does not exist" and "not yours", so ids cannot be probed
            return reply("I could not find claim #" + id + " among the claims you can view.",
                    List.of("Show my claims", "How do I submit a claim?"));
        }

        ExpenseResponse e = expenseService.toResponse(expense);
        List<String> lines = new ArrayList<>();
        lines.add("Claim #" + e.getId() + " (" + e.getCategory() + ")");

        boolean someoneElses = !expense.getUser().getId().equals(ctx.user().getId());
        if (someoneElses) {
            lines.add("• Employee: " + e.getEmployeeName()
                    + (e.getEmployeeDepartment() == null ? "" : " (" + e.getEmployeeDepartment() + ")"));
        }

        String amount = money(e.getAmountInBase());
        if (!"INR".equals(e.getCurrency())) {
            amount += " (entered as " + plain(e.getAmount()) + " " + e.getCurrency() + ")";
        }
        lines.add("• Amount: " + amount);
        lines.add("• Expense date: " + e.getExpenseDate().format(DATE));
        lines.add("• Status: " + e.getStatus());

        if (e.getHasReceipt()) {
            lines.add("• Receipt: uploaded");
        } else if (Boolean.TRUE.equals(e.getReceiptRequired())) {
            lines.add("• Receipt: required, not uploaded yet");
        } else {
            lines.add("• Receipt: not required");
        }

        if (e.getPolicyFlags().isEmpty()) {
            lines.add("• Policy: within the limits");
        } else {
            lines.add("• Policy warnings:");
            e.getPolicyFlags().forEach(f -> lines.add("   - " + f));
        }

        if (e.getStatus() == ExpenseStatus.REJECTED && e.getRejectionReason() != null) {
            lines.add("• Rejection reason: " + e.getRejectionReason());
        }
        if (e.getStatus() == ExpenseStatus.REIMBURSED) {
            Optional<PaymentResponse> payment =
                    expenseService.getPayment(ctx.user().getEmail(), ctx.privileged(), id);
            payment.ifPresent(p -> lines.add("• Payment: " + capitalize(p.paymentMethod().name())
                    + (p.referenceNo() == null ? "" : ", ref " + p.referenceNo())
                    + ", paid on " + p.paidOn().format(DATE)));
        }

        lines.add("");
        lines.add("Next step: " + nextStep(e, someoneElses));
        return reply(String.join("\n", lines),
                List.of("Show my claims", "How does reimbursement work?"));
    }

    private String nextStep(ExpenseResponse e, boolean someoneElses) {
        return switch (e.getStatus()) {
            case PENDING -> Boolean.TRUE.equals(e.getReceiptRequired()) && !e.getHasReceipt()
                    ? (someoneElses
                    ? "The receipt is missing, so it cannot be approved yet."
                    : "Upload the receipt so your manager can approve it.")
                    : "Waiting for a manager's decision.";
            case APPROVED -> "Approved. Waiting for an admin to reimburse it.";
            case REJECTED -> someoneElses
                    ? "Rejected. The employee can submit a new claim."
                    : "Rejected. You can fix the issue and submit a new claim.";
            case REIMBURSED -> "Done. The claim has been paid.";
        };
    }

    private AssistantResponse checkAmount(Context ctx, PolicyRule rule, BigDecimal amount) {
        List<String> lines = new ArrayList<>();
        lines.add("A " + rule.getCategory() + " claim of " + money(amount) + " (I treat the amount as INR):");

        if (rule.getMaxPerClaim() == null) {
            lines.add("• There is no per-claim limit.");
        } else if (amount.compareTo(rule.getMaxPerClaim()) > 0) {
            lines.add("• Over the per-claim limit of " + money(rule.getMaxPerClaim())
                    + ". It can still be submitted, but your manager will see an Over limit warning.");
        } else {
            lines.add("• Within the per-claim limit of " + money(rule.getMaxPerClaim()) + ".");
        }

        if (rule.getMonthlyLimit() != null) {
            YearMonth month = YearMonth.now();
            BigDecimal already = expenseRepository.sumInBase(
                    ctx.user().getId(), rule.getCategory(),
                    month.atDay(1), month.atEndOfMonth(), ExpenseStatus.REJECTED);
            BigDecimal before = already == null ? BigDecimal.ZERO : already;
            BigDecimal after = before.add(amount);
            lines.add("• This month you have claimed " + money(before) + " in " + rule.getCategory()
                    + " (rejected claims excluded). With this claim it would be " + money(after)
                    + ", against the monthly limit of " + money(rule.getMonthlyLimit()) + ". "
                    + (after.compareTo(rule.getMonthlyLimit()) > 0
                    ? "That is over the monthly limit, so it would be flagged."
                    : "That is within the limit."));
        }

        BigDecimal above = rule.getReceiptRequiredAbove();
        if (above == null) {
            lines.add("• A receipt is not required.");
        } else if (above.signum() == 0 || amount.compareTo(above) > 0) {
            lines.add("• A receipt is required, and you cannot submit without one.");
        } else {
            lines.add("• No receipt is needed at this amount (a receipt is required above "
                    + money(above) + ").");
        }

        lines.add("");
        lines.add("Claims in another currency are converted to INR when you submit, and the limits are checked on the INR amount.");
        return reply(String.join("\n", lines),
                List.of("What is the " + rule.getCategory() + " limit?", "When do I need a receipt?",
                        "How do I submit a claim?"));
    }

    // ---------- Suggestions ----------

    private List<String> defaultSuggestions(Context ctx) {
        List<String> list = new ArrayList<>(List.of(
                "What are the policy limits?",
                "Can I claim 7500 for travel?",
                "When do I need a receipt?",
                "How do I submit a claim?",
                "Show my claims"));
        if (ctx.privileged()) {
            list.set(3, "Show pending approvals");
        }
        if (ctx.admin()) {
            list.set(4, "How do I change the policy?");
        }
        return list;
    }

    private AssistantResponse reply(String answer, List<String> suggestions) {
        return new AssistantResponse(answer, suggestions);
    }

    // ---------- Reading the question ----------

    // Lower case, no punctuation, and numbers like 7,500 turned into 7500
    private String normalize(String raw) {
        String t = raw == null ? "" : raw.toLowerCase(Locale.ROOT);
        t = t.replaceAll("(?<=\\d),(?=\\d)", "");
        t = t.replace("₹", " ");
        t = t.replaceAll("[^a-z0-9#.\\s]", " ");
        t = t.replaceAll("(?<!\\d)\\.|\\.(?!\\d)", " ");
        return t.replaceAll("\\s+", " ").trim();
    }

    private boolean allIn(String[] tokens, Set<String> words) {
        if (tokens.length > 6) {
            return false;
        }
        for (String token : tokens) {
            if (!words.contains(token)) {
                return false;
            }
        }
        return true;
    }

    // Matches the start of a word, so "reject" also matches "rejected"
    private boolean matches(String text, String keyword) {
        return Pattern.compile("\\b" + Pattern.quote(keyword)).matcher(text).find();
    }

    private boolean wholeWord(String text, String word) {
        return Pattern.compile("\\b" + Pattern.quote(word) + "\\b").matcher(text).find();
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (matches(text, keyword)) {
                return true;
            }
        }
        return false;
    }

    // A longer phrase counts for more than a single word
    private int score(String text, List<String> keywords) {
        int total = 0;
        for (String keyword : keywords) {
            if (matches(text, keyword)) {
                total += 1 + 2 * (keyword.split(" ").length - 1);
            }
        }
        return total;
    }

    private PolicyRule findCategory(String text, List<PolicyRule> rules) {
        for (PolicyRule rule : rules) {
            String key = rule.getCategory().toLowerCase(Locale.ROOT);
            List<String> words = new ArrayList<>(CATEGORY_WORDS.getOrDefault(key, List.of()));
            if (!key.equals("other")) {
                words.add(key);
            }
            for (String word : words) {
                if (wholeWord(text, word)) {
                    return rule;
                }
            }
            if (key.equals("other")
                    && Pattern.compile("\\bother (category|expense|expenses|claim|claims)\\b")
                    .matcher(text).find()) {
                return rule;
            }
        }
        return null;
    }

    private Long findClaimId(String text, boolean hasCategory, int tokenCount) {
        Matcher m = HASH_ID.matcher(text);
        if (m.find()) {
            return Long.parseLong(m.group(1));
        }
        m = EXPLICIT_ID.matcher(text);
        if (m.find()) {
            return Long.parseLong(m.group(1));
        }

        // "claim 12" counts as an id only when it is clearly a status question,
        // so "can I claim 5000 for food" is not mistaken for claim number 5000
        if (!hasCategory && (tokenCount <= 3
                || containsAny(text, "status", "what", "where", "about", "check", "show", "detail", "why"))) {
            m = PLAIN_ID.matcher(text);
            if (m.find()) {
                return Long.parseLong(m.group(1));
            }
        }
        return null;
    }

    private BigDecimal findAmount(String text) {
        Matcher m = AMOUNT.matcher(text);
        while (m.find()) {
            BigDecimal value = new BigDecimal(m.group(1));
            String unit = m.group(2);
            if (unit != null) {
                value = switch (unit) {
                    case "k", "thousand" -> value.multiply(BigDecimal.valueOf(1000));
                    default -> value.multiply(BigDecimal.valueOf(100000));
                };
            }
            if (value.signum() > 0 && value.compareTo(BigDecimal.valueOf(1_000_000_000L)) < 0) {
                return value;
            }
        }
        return null;
    }

    private ExpenseStatus statusIn(String text) {
        if (matches(text, "pending")) return ExpenseStatus.PENDING;
        if (matches(text, "reimbursed")) return ExpenseStatus.REIMBURSED;
        if (matches(text, "approved")) return ExpenseStatus.APPROVED;
        if (matches(text, "rejected")) return ExpenseStatus.REJECTED;
        return null;
    }

    // ---------- Formatting ----------

    private String ruleLines(PolicyRule r) {
        return "• Per-claim limit: " + (r.getMaxPerClaim() == null ? "no limit" : money(r.getMaxPerClaim()))
                + "\n• Monthly limit: "
                + (r.getMonthlyLimit() == null ? "no limit" : money(r.getMonthlyLimit()) + " per employee")
                + "\n• Receipt: " + receiptText(r);
    }

    private String receiptText(PolicyRule r) {
        if (r.getReceiptRequiredAbove() == null) {
            return "not required";
        }
        if (r.getReceiptRequiredAbove().signum() == 0) {
            return "always required";
        }
        return "required above " + money(r.getReceiptRequiredAbove());
    }

    private String claimLine(Expense e) {
        return "• #" + e.getId() + " " + e.getCategory() + " " + money(inBase(e))
                + " on " + e.getExpenseDate().format(DATE) + " (" + e.getStatus() + ")";
    }

    private BigDecimal inBase(Expense e) {
        return e.getAmountInBase() != null ? e.getAmountInBase() : e.getAmount();
    }

    private BigDecimal sum(List<Expense> list) {
        return list.stream().map(this::inBase).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private boolean hasReceipt(Expense e) {
        return e.getReceiptPath() != null && !e.getReceiptPath().isBlank();
    }

    private boolean hasFlag(Expense e) {
        return e.getPolicyFlags() != null && !e.getPolicyFlags().isBlank();
    }

    private String money(BigDecimal value) {
        return "₹" + plain(value);
    }

    private String plain(BigDecimal value) {
        NumberFormat format = NumberFormat.getNumberInstance(INDIA);
        format.setMinimumFractionDigits(0);
        format.setMaximumFractionDigits(2);
        return format.format(value);
    }

    private String capitalize(String value) {
        String lower = value.toLowerCase(Locale.ROOT).replace('_', ' ');
        return lower.substring(0, 1).toUpperCase(Locale.ROOT) + lower.substring(1);
    }
}