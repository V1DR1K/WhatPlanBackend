package com.wherefood.journey;

import static org.assertj.core.api.Assertions.*;

import com.wherefood.domain.JourneyMovement;

import jakarta.servlet.http.HttpServletRequest;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;

class JourneyRulesTest {
    @Test
    void cityAliasKeepsLegacyRequestsAndRejectsContradictoryParameters() throws Exception {
        CityFilterAlias filter = new CityFilterAlias();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/films");
        request.addParameter("cityId", "2");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(
                request,
                response,
                (req, res) ->
                        assertThat(((HttpServletRequest) req).getParameter("zoneId"))
                                .isEqualTo("2"));
        request.addParameter("zoneId", "1");
        response = new MockHttpServletResponse();
        filter.doFilter(
                request,
                response,
                (req, res) -> {
                    throw new AssertionError("must reject ambiguous location");
                });
        assertThat(response.getStatus()).isEqualTo(400);
    }

    @Test
    void cityAliasDoesNotParseMultipartRequestsBeforeTheMvcResolver() throws Exception {
        CityFilterAlias filter = new CityFilterAlias();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/whither-journey/1/photos") {
            @Override
            public String getParameter(String name) {
                throw new AssertionError("multipart parameters must be left to Spring MVC");
            }
        };
        request.setContentType("multipart/form-data; boundary=photo-boundary");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> assertThat(req).isSameAs(request));

        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void fileTypesUseContentSignatureAndRejectHtmlDisguisedAsPdf() {
        assertThat(JourneyService.fileType("%PDF-1.7".getBytes(StandardCharsets.US_ASCII)))
                .isEqualTo("application/pdf");
        assertThat(JourneyService.fileType(new byte[] {(byte) 255, (byte) 216, (byte) 255}))
                .isEqualTo("image/jpeg");
        assertThat(JourneyService.fileType(new byte[] {(byte) 137, 80, 78, 71, 13, 10, 26, 10}))
                .isEqualTo("image/png");
        assertThat(JourneyService.fileType("RIFF0000WEBP".getBytes(StandardCharsets.US_ASCII)))
                .isEqualTo("image/webp");
        assertThatThrownBy(
                        () ->
                                JourneyService.fileType(
                                        "<html>not PDF</html>".getBytes(StandardCharsets.US_ASCII)))
                .hasMessageContaining("PDF, JPEG, PNG y WebP");
    }

    @Test
    void balanceArithmeticPreservesDecimalsAndCurrencies() {
        var fund = movement("ARS", "FUNDS", "0.30");
        var expense = movement("ARS", "EXPENSE", "0.10");
        var usd = movement("USD", "REFUND", "2.1234");
        var b = JourneyService.balances(List.of(fund, expense, usd));
        assertThat(b).hasSize(2);
        assertThat(b.getFirst().balance()).isEqualByComparingTo("0.20");
        assertThat(b.getLast().balance()).isEqualByComparingTo("2.1234");
    }

    @Test
    void decimalContractRetainsFourDigitsBeyondJavascriptIntegerPrecision() throws Exception {
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules();
        var amount = new BigDecimal("99999999999999.9899");
        var dto =
                new JourneyDtos.MovementDto(
                        java.util.UUID.randomUUID(),
                        null,
                        null,
                        null,
                        "EXPENSE",
                        "Exact decimal",
                        amount,
                        "ARS",
                        java.time.LocalDate.of(2026, 8, 10));
        var json = mapper.readTree(mapper.writeValueAsString(dto));
        assertThat(json.get("amount").isTextual()).isTrue();
        assertThat(json.get("amount").asText()).isEqualTo("99999999999999.9899");
        var request =
                mapper.readValue(
                        "{\"kind\":\"EXPENSE\",\"description\":\"Exact"
                            + " decimal\",\"amount\":\"99999999999999.9899\",\"currency\":\"ARS\",\"occurredOn\":\"2026-08-10\"}",
                        JourneyDtos.MovementRequest.class);
        assertThat(request.amount()).isEqualByComparingTo(amount);
    }

    private JourneyMovement movement(String currency, String kind, String amount) {
        JourneyMovement m = new JourneyMovement();
        m.currency = currency;
        m.kind = kind;
        m.amount = new BigDecimal(amount);
        return m;
    }
}
