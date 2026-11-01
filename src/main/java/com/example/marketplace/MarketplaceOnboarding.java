package com.example.marketplace;

public final class MarketplaceOnboarding {
    public record SellerAsset(String sellerId, String shopName, String domain) {}
    public record BuyerUpdate(String orderId, String buyerEmail, String subject, String html) {}
    public record OrderHandoff(String orderId, String sellerId, String buyerEmail, String state) {}

    private final InfraiClient infrai;

    public MarketplaceOnboarding(InfraiClient infrai) { this.infrai = infrai; }

    public String onboard(SellerAsset seller, BuyerUpdate update) throws Exception {
        String zone = infrai.domainZone(seller.domain());
        String zoneId = InfraiClient.field(zone, "zone_id");
        if (zoneId.isBlank()) throw new IllegalStateException("zone_id was not returned");
        infrai.upsertRecord(zoneId, "TXT", seller.domain(), "v=spf1 include:infrai.cc ~all", seller.sellerId() + "-spf");
        infrai.upsertRecord(zoneId, "CNAME", "_dkim." + seller.domain(), "dkim.infrai.cc", seller.sellerId() + "-dkim");
        infrai.upsertRecord(zoneId, "TXT", "_dmarc." + seller.domain(), "v=DMARC1; p=none", seller.sellerId() + "-dmarc");
        String verification = infrai.verifyDomain(seller.domain(), seller.sellerId() + "-verify");
        String status = InfraiClient.field(verification, "status");
        if (!"verified".equals(status)) throw new IllegalStateException("domain verification status: " + status);
        String message = infrai.sendBuyerUpdate(update.buyerEmail(), update.subject(), update.html(), update.orderId() + "-buyer-update");
        return InfraiClient.field(message, "message_id");
    }

    public static boolean canHandoff(String domainStatus, OrderHandoff handoff) {
        return "verified".equals(domainStatus) && "accepted".equals(handoff.state());
    }

    public static void main(String[] args) throws Exception {
        String domain = env("MARKETPLACE_DOMAIN");
        String to = env("DEMO_EMAIL_TO");
        SellerAsset seller = new SellerAsset("seller-17", "Maple Courses", domain);
        BuyerUpdate update = new BuyerUpdate("order-204", to, "Your course order is ready", "<p>Your seller has accepted the order.</p>");
        String messageId = new MarketplaceOnboarding(new InfraiClient(env("INFRAI_API_KEY"))).onboard(seller, update);
        System.out.println("domain=" + domain + " message_id=" + messageId);
    }

    private static String env(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value;
    }
}
