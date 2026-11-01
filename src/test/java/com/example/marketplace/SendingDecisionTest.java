package com.example.marketplace;

public final class SendingDecisionTest {
    public static void main(String[] args) {
        var accepted = new MarketplaceOnboarding.OrderHandoff("order-204", "seller-17", "buyer@example.org", "accepted");
        check(MarketplaceOnboarding.canHandoff("verified", accepted));
        check(!MarketplaceOnboarding.canHandoff("pending", accepted));
        System.out.println("SendingDecisionTest passed");
    }

    private static void check(boolean result) {
        if (!result) throw new AssertionError("handoff decision did not match the domain rule");
    }
}
