package org.smartcoders.expensereimbursementandapprovalsystem.controller;

import lombok.RequiredArgsConstructor;
import org.smartcoders.expensereimbursementandapprovalsystem.service.ExchangeRateService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/currencies")
@RequiredArgsConstructor
public class CurrencyController {

    private final ExchangeRateService exchangeRateService;

    @GetMapping
    public ResponseEntity<List<ExchangeRateService.CurrencyInfo>> list() {
        return ResponseEntity.ok(exchangeRateService.getCurrencies());
    }

    @GetMapping("/rate")
    public ResponseEntity<ExchangeRateService.RateQuote> rate(@RequestParam String currency) {
        return ResponseEntity.ok(exchangeRateService.quote(currency));
    }
}