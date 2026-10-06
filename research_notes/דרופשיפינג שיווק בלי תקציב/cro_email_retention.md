# CRO, email/SMS, retention, brand-building, CS/chargebacks, affiliate/UGC and marketplaces for zero-budget dropshipping stores (as of Oct 2026)

**Research caveat (read first).** The network proxy blocked direct fetches of nearly all primary sources (Baymard, Klaviyo, Brevo, Omnisend, Shopify, Littledata, Etsy, eBay pricing/policy pages). All findings below come from web-search result summaries that name the cited URL. Where the cited URL is a third-party aggregator/vendor blog rather than the primary source, it is marked **[secondary]**. Numbers from SEO/vendor blogs often lack methodology and should be treated as directional. Primary-source verification (pricing pages, policy pages) is still needed before the final report states exact limits. Today = 2026-10-06.

---

## 1. CRO: what store/product-page elements move conversion

### Takeaway
Baseline Shopify conversion is roughly 1.4-2.6% (median ~1.4-1.7%), so a zero-budget store getting a handful of visitors needs to fix trust, shipping clarity, mobile checkout and review proof first, not fancy tests. The best-evidenced levers are: reviews (Spiegel), accelerated checkout (Shop Pay, Shopify-claimed), cost/delivery transparency and no forced account (Baymard), and page speed (Google/Deloitte). Statistically valid A/B testing is not realistic at tiny traffic; use best-practice implementation instead.

### Cited Findings
- Baseline benchmarks: Shogun Jan-Jun 2026 study of 745 Shopify stores reports 2.61% mean and 1.74% median conversion; another source reports a median of 1.4% (421 stores); top 20% above ~2.6%, top 10% ~3.4%+; IRP Commerce 2.26% (July 2026); Dynamic Yield 2.72% global — [Littledata / aggregator compilation](https://www.littledata.io/ecommerce-conversion-rate) and [aggregators](https://easyappsecom.com/guides/shopify-conversion-rate-benchmarks) **[secondary; sources disagree, figure depends on the dataset]**.
- Baymard cart abandonment average is 70.22% (meta-analysis of 50+ studies); mobile 85.65%, tablet 80.74%, desktop 73.07% — [Baymard](https://baymard.com/research-articles/ecommerce-checkout-usability-report-and-benchmark) (figures as relayed by search summary and [zerocartai](https://zerocartai.com/blog/baymard-cart-abandonment-rate-2026) **[secondary]**). Mobile-first design matters most because mobile abandons far more.
- Baymard abandonment reasons (US shoppers, last 3 months): extra costs (shipping/tax/fees) too high 40%; forced account creation 18%; checkout too long/complicated 17%; delivery too slow 20%; did not trust site with card details 19%; unsatisfactory return policy 15% — [Baymard](https://baymard.com/research-articles/ecommerce-checkout-usability-report-and-benchmark); [Baymard return policy/product page](https://baymard.com/research/product-page).
- Baymard estimates ~$260B in recoverable orders in US/EU from better checkout design alone — [Baymard](https://baymard.com/blog/reduce-cart-abandonment) (via search summary).
- Delivery-date clarity: 41% of sites show "shipping speed" rather than an estimated delivery date, creating uncertainty; Baymard recommends showing a delivery date per shipping option — [Baymard](https://baymard.com/blog/shipping-speed-vs-delivery-date).
- Return policy visibility: about 60% of users look for returns info on the product page, yet 44% of leading sites do not show/link it there — [Baymard](https://baymard.com/research/product-page); [Baymard footer links](https://baymard.com/research-articles/footer-needs-return-shipping-links).
- Reviews: Northwestern Spiegel Research Center (with PowerReviews) found purchase likelihood for a product with 5 reviews is 270% higher than with none (190% for lower-priced items, 380% for higher-priced); marginal benefit drops quickly after ~5 reviews; purchase likelihood peaks at average ratings of 4.0-4.7, not 5.0 — [Spiegel Research Center](https://spiegel.medill.northwestern.edu/how-online-reviews-influence-sales/) (figures relayed via search summary of [Lipscore](https://lipscore.com/blog/product-reviews-increase-your-online-sales-by-270/)). Note: this is the strongest academic data point but dates from ~2017 (stale; direction still widely cited).
- Shop Pay: Shopify claims, citing an external consulting-firm study, that Shop Pay lifts conversion by up to 50% vs guest checkout and beats other accelerated checkouts by at least 10%; mere presence adds ~5% lower-funnel lift; Shopify checkout overall converts up to 36% (avg 15%) better than competing platforms — [Shopify blog](https://www.shopify.com/blog/shop-pay-checkout). **Vendor-claimed, "up to", methodology undisclosed.** Apple Pay/Google Pay are other accelerated options (no independent figure found).
- Page speed: Google/Deloitte "Milliseconds Make Millions" (37 brands) found a 0.1s mobile speed gain raised retail conversion ~8.4% and AOV ~9.2%; Portent data: conversion falls ~4.42% per extra second of load between 0-5s; a 1s page converts ~2.5x a 5s page (Portent 2022) — [Mirasvit summary](https://mirasvit.com/blog/page-speed-conversion-rate.html) **[secondary]**; study is from 2019-2020 (stale). Implication: avoid heavy apps/themes.
- Product video: claims range from "converts 4.8% vs 2.9% without video (+65%)" to "up to 80%" — [Invesp](https://www.invespcro.com/blog/e-commerce-product-videos/) and [Xictron](https://www.xictron.com/en/blog/product-videos-e-commerce-conversion-2026/) **[secondary/vendor, correlation not causation; likely selection bias]**.
- Trust badges: 18% of users explicitly look for security indicators before entering card details; badge recognition matters (Norton picked by 35.4% of respondents; a DIY seal beat several vendor SSL seals in the Baymard-linked study) — [Baymard perceived security](https://baymard.com/blog/perceived-security-of-payment-form); [The SSL Store summary of Baymard study](https://www.thesslstore.com/blog/new-baymard-study-how-to-improve-ecommerce-checkout-rates-with-site-seals-checkout-design/) **[secondary]**. Recognised payment logos (PayPal, Shop Pay, Visa) are likely a better trust cue than generic fake-looking badges.
- Abandoned checkout emails sent from Shopify do not count against Shopify Email's free quota (see section 3).

### Inferences
- With under ~100 sessions/month, A/B tests cannot reach significance; implement the Baymard/Spiegel-style fixes directly: show total cost incl. shipping early, guest checkout, delivery date range, returns link on product page, 5+ reviews, fast lightweight theme, Shop Pay/Apple Pay/PayPal enabled.
- Mobile abandonment (~86%) means test the store on a phone before anything else.
- Because many "lift" numbers are vendor-sourced with "up to" language, the final report should present them as directional ranges, not guarantees.

### Gaps
- Could not access Littledata/Shopify primary pages (blocked); no verified independent figure for Apple Pay/Google Pay lift.
- No credible A/B data on conversion effect specifically for dropshipping stores with <1,000 sessions.
- Product-video lift figures lack causal evidence.

---

## 2. Trust signals for a new, unknown store

### Takeaway
Trust is the main barrier: Baymard shows 19% abandon from distrust of card handling, 20% over slow delivery, 15% over returns. The cheapest effective moves are honest delivery windows, a visible returns/contact section, reviews (Judge.me free), and recognisable payment logos; slow/misleading shipping also drives the chargebacks that kill processor accounts.

### Cited Findings
- Distrust of site with card details: 19% of US shoppers abandoned a checkout in the last 3 months for that reason — [Baymard](https://baymard.com/research-articles/ecommerce-checkout-usability-report-and-benchmark).
- Slow delivery: 20% abandon because delivery is too slow — [Baymard](https://baymard.com/research-articles/ecommerce-checkout-usability-report-and-benchmark).
- Review apps: Judge.me Free = unlimited review requests by email, unlimited storage/display, photo and video reviews, SEO rich snippets, carousels, star and trust badges, but Judge.me branding; Awesome plan $15/month removes branding and adds AI/Q&A — [WiserReview Judge.me review](https://wiserreview.com/blog/judge-me-review/); [Eevy pricing](https://eevy.ai/blog/judgeme-pricing) **[secondary]**.
- Loox: no genuinely free full plan; the "Beginner" tier is reviews-only at $0 or ~$9.99 with referrals, capped at ~100 orders/month and 500 reviews in total; Convert $49.99/month; paid tiers scale by order volume — [WiserReview Loox pricing](https://wiserreview.com/blog/loox-pricing/); [Loox vs Judge.me](https://wiserreview.com/blog/loox-vs-judge-me/) **[secondary, vendor-comparison site; verify on Shopify App Store]**.
- Rating psychology: purchase likelihood peaks at 4.0-4.7 stars; a flawless 5.0 can look fake — [Spiegel Research Center](https://spiegel.medill.northwestern.edu/how-online-reviews-influence-sales/).
- Amazon-style marketplace rules force "seller of record" branding on packaging/packing slips (see section 8), which is consistent with branded inserts as a trust practice — [AutoDS](https://www.autods.com/blog/suppliers-marketplaces/amazon-dropshipping-policy/) **[secondary]**.

### Inferences
- Honest delivery times (e.g., "8-15 business days") reduce "not received" disputes and match Baymard's finding on delivery-date clarity; over-promising is what generates chargebacks.
- Imported/AliExpress-style reviews carry compliance risk (fake reviews, FTC/EU rules) — not researched here; recommend report writer check separately.
- Branded packaging/inserts via supplier: no primary source found quantifying the lift (see Gaps).

### Gaps
- No quantitative evidence found for conversion effect of branded inserts or custom packaging from dropshipping suppliers.
- Did not verify Judge.me/Loox pricing on primary pages (blocked).
- No data found on response-time-to-conversion effect for live chat/email at small stores.

---

## 3. Email/SMS: free tiers, flows, benchmarks, pop-ups, legal

### Takeaway
For a zero-budget store, Shopify Email (10,000 free/month) and Brevo (9,000/month free) have the most usable free allowances; Klaviyo, Omnisend and Mailchimp free tiers shrank to ~250 contacts/500 sends. Automated flows (abandoned cart, welcome) are the highest revenue-per-recipient emails; SMS should be avoided/limited at zero budget because consent rules are strict and it costs credits.

### Cited Findings
**Free tiers (2026)**
- Klaviyo free: 250 active profiles, 500 emails/month, 150 monthly mobile messaging credits, Klaviyo branding, email support only first 60 days, no A/B testing in campaigns/flows; Email plan from $20/month (251-500 contacts, 5,000 emails); Email+SMS from $35 with 1,250 SMS credits — [Omnisend's Klaviyo pricing post](https://www.omnisend.com/blog/klaviyo-pricing/); [EmailToolTester](https://www.emailtooltester.com/en/reviews/klaviyo/pricing/) **[secondary; Omnisend is a competitor]**. Caveat: the Shopify sync counts all customers as active profiles, so past buyers can exceed 250 — [Email Software Insights](https://www.emailsoftwareinsights.com/reviews/klaviyo/pricing/free-plan/) **[secondary]**.
- Mailchimp free: cut in Jan 2026 from 500 contacts/1,000 sends to 250 contacts/500 sends (effective Feb 17, 2026); automations had already been removed from free in mid-2025; no scheduling, no A/B testing, 30-day support, Mailchimp badge — [Groupmail](https://blog.groupmail.io/mailchimp-free-plan-changes-2026/); [Audienceful](https://www.audienceful.com/blog/mailchimp-price-increase) **[secondary]**.
- Omnisend free: 250 contacts, 500 emails/month across marketing and transactional — [Omnisend vs Mailchimp / Brevo alternatives](https://www.omnisend.com/blog/brevo-alternatives/); [Brevo comparison](https://www.brevo.com/blog/mailchimp-alternatives/) **[vendor blogs]**.
- Brevo free: up to 100,000 contacts stored, 300 emails/day (~9,000/month), automation for up to 2,000 contacts — [Brevo blog](https://www.brevo.com/blog/mailchimp-alternatives/) **[vendor self-reported; verify on brevo.com/pricing]**.
- Shopify Email: included on all Shopify plans; first 10,000 emails/month free, then $1 per 1,000 (to 300,000); abandoned-checkout automation emails do not count toward the total — [Sequenzy Shopify Email pricing](https://www.sequenzy.com/pricing/shopify-email); [Synder](https://synder.com/fees-shopify/shopify-email-fee/) **[secondary]**.

**Flow benchmarks**
- Klaviyo: abandoned cart flow has the highest revenue per recipient (RPR) of any flow, ~$3.65 on average in Klaviyo's 2024 benchmark (one source cites $3.07); elite performers up to $28.89; average abandoned-cart placed-order rate ~3.33% (top performers ~7.69%); open rate ~50.5% — [Klaviyo benchmark page](https://www.klaviyo.com/marketing-resources/ecommerce-benchmarks); [Attribuly](https://attribuly.com/blogs/abandoned-cart-recovery-rate-klaviyo/); [B&S flow benchmarks](https://bsandco.us/blog-post/klaviyo-flow-benchmarks) **[secondary; figures vary by source/year]**.
- Welcome flow: ~$2.65 RPR; top 10% placed order rate 10.53% — [B&S flow benchmarks](https://bsandco.us/blog-post/klaviyo-flow-benchmarks) **[secondary]**.
- Omnisend abandoned-cart automation: 37.12% open, 4.13% CTR, 1.72% conversion, $3.59 per email — [Omnisend benchmarks](https://www.omnisend.com/blog/email-marketing-benchmarks/) (via search summary).
- Flow revenue target of 50-60% of total email revenue for mature Klaviyo brands; automated flows can generate up to 30x RPR of campaigns — [Darkroom Agency](https://www.darkroomagency.com/observatory/email-marketing-benchmarks-ecommerce-2026) **[secondary; mature-brand benchmark, not tiny stores]**.

**Pop-ups / lead magnets**
- Reported signup conversion rates: 10% off ~2.5%, 15% off ~3.8%; "good" standard offer 7-10%; spin-to-win and mystery discounts 12-18%; time+exit-intent combos ~9.4% vs time delay ~4.2% — [Wisepops](https://wisepops.com/blog/klaviyo-popup); [Ecommerce Intelligence](https://www.ecommerceintelligence.com/klaviyo-popups-signup-forms/) **[secondary/vendor; wide range, contradictory (2.5% vs 7-10% "good")]**.

**Legal (summary, not legal advice)**
- US TCPA: prior express (written) consent required for marketing texts; damages up to $1,500 per message; STOP must be honored (one source: within 10 business days). The FCC "one-to-one consent" rule is no longer in effect; Bradford v. Sovereign Pest Control (5th Cir., Feb 25, 2026) held TCPA requires only "prior express consent" for telemarketing texts — [Omnisend SMS regulations](https://www.omnisend.com/blog/sms-regulations/); [ActiveProspect](https://activeprospect.com/blog/tcpa-text-messages/) **[secondary; regional court ruling, unsettled—verify]**.
- EU: GDPR + ePrivacy require freely given, specific, informed, unambiguous consent for marketing SMS; no pre-ticked boxes; easy withdrawal — [Digital Applied](https://www.digitalapplied.com/blog/sms-marketing-compliance-tcpa-gdpr-guide-2026) **[secondary]**.
- Israel (relevant if the operator or recipients are in Israel): Section 30A of the Communications Law prohibits advertising by email/SMS/fax/robocall without prior opt-in consent; messages must be labelled as advertisement ("פרסומת") in the subject line and give sender identity and an opt-out right — [Lexology](https://www.lexology.com/library/detail.aspx?g=dfa80306-8bc5-4ac3-b01e-193ccd49dbb8); [DataGuidance](https://www.dataguidance.com/notes/israel-smsmms-marketing); [Hunton](https://www.hunton.com/privacy-and-information-security-law/new-anti-spam-law-takes-effect-in-israel).

### Inferences
- Zero-budget best stack: Shopify Email (abandoned checkout is free and uncapped; 10k/month) for flows/campaigns; Brevo free as list-building alternative; Klaviyo free only if list stays <250 profiles, noting its Shopify sync quirk.
- Realistic revenue: if a store gets e.g. 500 visitors, a handful of carts, and 3% recovery, abandoned-cart email contributes a few orders at most; value rises with traffic. Do not expect "flows = 50% of email revenue" at the start.
- SMS: free tiers give only ~150 credits (Klaviyo) and international SMS costs/consent complexity are high; email-first for global sales is the safe recommendation.

### Gaps
- Primary pricing pages for Klaviyo/Brevo/Omnisend/Mailchimp were blocked; verify all limits before publication.
- No independent benchmark for flow performance at stores with <500 subscribers.
- Cross-border SMS cost per message by country not researched.
- No authoritative source found on the share of total store revenue from email for new stores.

---

## 4. Retention and LTV

### Takeaway
Repeat-purchase dropshipping works mainly for consumables and niche-hobby replenishment; consumable categories show 35-50% repeat rates in aggregator benchmarks, versus ~19-28% overall. Gadget/one-off products rarely repeat, so bundles/upsells for AOV and email replenishment flows matter most.

### Cited Findings
- Average e-commerce repeat purchase rate cited at 28.2% (another benchmark 18.8% over 156,000 DTC customers); consumables (supplements, food, pet supplies, coffee) 35-50%, top 20% 60%+ — [Finsi](https://www.finsi.ai/blog/repeat-purchase-rate-ecommerce/); [Rivo](https://www.rivo.io/blog/repeat-purchase-rate-complete-guide) **[secondary; methodology unclear]**.
- Average Shopify 3-year LTV $168 in 2026; top 25% $250-$450; bottom 25% $55-$90; food & beverage $310 (52% repeat), health/supplements $285 (45% repeat) — [EasyApps benchmarks](https://easyappsecom.com/guides/shopify-customer-lifetime-value-benchmarks) **[secondary, low-rigor]**.
- Repeat customers spend ~67% more per order than first-time buyers — [Rivo / aggregators](https://www.rivo.io/blog/repeat-purchase-rate-complete-guide) **[secondary]**.
- Free/cheap AOV apps: Aftersell (post-purchase one-click upsell, free plan), Kaching Bundles (free to install), Honeycomb (free post-purchase and cross-sell), ReConvert (thank-you page), Selleasy — [Shopify App Store category](https://apps.shopify.com/categories/marketing-and-conversion-upsell-and-bundles-upsell-and-cross-sell/all); [Digismoothie](https://www.digismoothie.com/best-shopify-apps/upsell). Free-plan status changes often; verify each.
- Loyalty/referral: Smile.io has a free plan (referrals on $49+/month plans) — [Retainful roundup](https://www.retainful.com/blog/best-shopify-referral-apps) (dated 2024 listing; **stale**).

### Inferences
- A dropshipped consumable niche (e.g., pet supplies, hobby consumables) is the best-fit category for an LTV asset; but dropshipped consumables carry regulatory and quality risks (not researched).
- With no verified AOV-lift numbers from independent sources, treat upsell uplift as unverified.

### Gaps
- No credible dropshipping-specific repeat rate data (benchmarks are DTC/Shopify-wide).
- No independent A/B data for AOV lift from bundles/upsells for small stores.

---

## 5. Brand vs generic store

### Takeaway
Direction is consistent across practitioner commentary that a niche, branded store converts and retains better and survives platform scrutiny better than a "random product" store, but I found no rigorous source quantifying it; the headline "40-60% better" is unsourced vendor content.

### Cited Findings
- Claim: niche-specific stores outperform general stores by 40-60%; paid CAC up ~40% in two years to $68-$84 — [Branvas](https://branvas.com/blogs/news/the-new-era-of-dropshipping-from-generic-hustles-to-brand-driven-businesses); [Branvas stats](https://branvas.com/blogs/news/dropshipping-statistics) **[low-quality, vendor blog, no cited methodology — treat as uncertain]**.
- Conversion rates vary by AOV/category: fine jewelry (>$500 AOV) 0.8-1.0% vs fashion jewelry (<$100) 2.5-3.0% — [Branvas](https://branvas.com/blogs/news/dropshipping-statistics) **[secondary]**; category consumables reach 3-5% — [Littledata/aggregators](https://www.littledata.io/ecommerce-conversion-rate).
- Marketplace and payment rules reward real seller identity: Amazon requires the seller to be seller of record with branded packing slips (section 8); Visa/Mastercard monitoring penalizes high dispute ratios regardless of brand (section 6).

### Inferences
- Minimum viable brand at $0 (inference, not source-based): a specific niche and name, a clean theme, a logo from a free tool, a consistent About page with real business details, branded email sender, branded thank-you/insert (check supplier support), real contact email, written policies.

### Gaps
- No rigorous comparative study (branded vs generic dropshipping) found; no verified CAC data for zero-budget organic stores.

---

## 6. Customer service, refunds, chargebacks as marketing; payment processor risk

### Takeaway
Slow shipping and poor communication produce "item not received" disputes, and card-network thresholds are now tight: Visa VAMP ratio 1.5% from April 2026 (US/EU/etc.), Shopify Payments flags around 1%. Prevention (honest ETAs, tracking, fast replies, proactive refunds) is cheaper than disputing.

### Cited Findings
- Visa VAMP merchant threshold 1.5% (down from 2.2%) from April 1, 2026 for US, Canada, EU, APAC, LATAM (CEMEA stays 2.2%); ratio combines fraud reports (TC40) and disputes (TC15) over settled transactions; $8 fee per disputed/fraud transaction; applies to accounts with 1,500+ transactions; Mastercard 1.5% with 100+ disputes — [Chargeflow thresholds](https://www.chargeflow.io/blog/chargeback-thresholds); [Chargeflow VAMP](https://www.chargeflow.io/blog/vamp-visa-acquirer-monitoring-program) **[secondary, dispute-tool vendor; thresholds differ across sources and timing; verify with Visa]**.
- Shopify Payments: reported 1% chargeback-to-transaction threshold for suspension, enforcing card-network limits; terminated accounts often have funds held 90-180 days; refund rate >2% triggers review at many processors — [Chargeflow Shopify Payments](https://www.chargeflow.io/blog/shopify-payments-account-suspended); [Payment Nerds](https://paymentnerds.com/blog/dropshipping-payment-processing-prevent-chargebacks-avoid-account-shutdowns/); [Shopify Community thread: 20% reserve due to dropshipping](https://community.shopify.com/t/20-reserve-on-payouts-because-of-dropshipping/347340) **[secondary; the 1% and 2% figures are rules of thumb from vendors, not official Shopify policy; official Shopify figure not verified]**.
- Slow delivery is the #1 trigger for "item not received" chargebacks; 26% of chargebacks are for product not arriving — [Payment Nerds](https://paymentnerds.com/blog/dropshipping-payment-processing-prevent-chargebacks-avoid-account-shutdowns/) **[secondary]**.
- PayPal can hold 10-30% of revenue in rolling reserves for 45-90 days; new/flagged accounts face 21-day holds — [Medium PayPal guide for dropshippers](https://medium.com/sellerprotection/ultimate-paypal-guide-for-dropshippers-e90df1decf6a) **[secondary, practitioner blog]**.

### Inferences
- With low transaction counts, a few disputes can push ratios above 1% quickly (e.g., 2 disputes in 150 orders = 1.3%), even though VAMP penalties apply only at 1,500+ transactions. Proactive refunds before a dispute is filed protect the ratio.
- CS responsiveness as marketing: reviews and Google/Trustpilot ratings are collected after good service (no quantitative source found).

### Gaps
- Official Shopify Payments/Stripe/PayPal policy pages not accessed; real-world data on how fast new stores are shut down is anecdotal.
- No dropshipping CS response-time benchmark found.

---

## 7. Affiliate/referral and UGC/review seeding

### Takeaway
Free options exist (Shopify Collabs, Judge.me), but I found no reliable data on effectiveness for zero-audience stores; treat as low-confidence tactics.

### Cited Findings
- Shopify Collabs app is free to install, with a 2.9% commission processing fee on automatic payments — [Hulk Apps comparison](https://www.hulkapps.com/blogs/compare/shopify-affiliate-program-apps-referral-candy-affiliate-vs-shopify-collabs) **[secondary; verify]**.
- ReferralCandy has no free plan (Premium $59/month after 14-day trial); Smile.io has a free tier — [ReferralCandy](https://www.referralcandy.com/); [WiserReview roundup](https://wiserreview.com/blog/shopify-referral-apps/) **[secondary/vendor]**.
- Review collection: Judge.me free plan includes automated review request emails and photo/video reviews — [WiserReview](https://wiserreview.com/blog/judge-me-review/).

### Inferences
- Product-seeding to micro-creators and post-purchase review requests are the practical zero-cost routes; commission-only affiliates cost nothing until a sale. No source quantifies response rates.

### Gaps
- No evidence found on UGC seeding conversion or affiliate recruitment success rates for new stores.

---

## 8. Marketplaces as extra zero-cost traffic (2026 rules)

### Takeaway
Marketplace dropshipping is heavily constrained: Etsy effectively bans reselling generic products, eBay bans retail-to-retail dropshipping (wholesale supplier only), Amazon requires you to be the seller of record, and TikTok Shop imposes fast-dispatch SLAs plus (reported) US-entity requirements. Beginners from Israel/global should treat marketplaces as a later step, except eBay with real wholesale suppliers or Etsy with original/POD designs.

### Cited Findings
- Etsy: allowed only for production partners making your own design (e.g., print-on-demand with original designs), genuinely custom orders, vintage 20+ years, and limited craft supplies; ready-made wholesale/retail goods (AliExpress, Amazon) are prohibited — [Dropship.io](https://www.dropship.io/blog/etsy-dropshipping); [Bettamax](https://bettamax.com/etsy-dropshipping/) **[secondary; primary Etsy Creativity Standards page blocked, verify]**.
- eBay: dropshipping allowed only from a wholesale supplier authorized to sell to you; buying from another retailer (Amazon, Walmart, Target) and shipping to eBay buyers is prohibited, with suspension risk — [AutoDS](https://www.autods.com/blog/dropshipping-tips-strategies/ebay-dropshipping-policy/); [Super-DS](https://super-ds.com/blog/ebay-dropshipping-policy-2026) **[secondary; primary eBay policy page blocked, verify]**.
- Amazon: allowed if you are the seller of record, identify yourself on packing slips/invoices/packaging, remove supplier identifiers before shipment, and handle returns; buying from another retailer that ships under its own identity is prohibited — [AutoDS Amazon policy](https://www.autods.com/blog/suppliers-marketplaces/amazon-dropshipping-policy/); [Importify](https://www.importify.com/blog/amazon-dropshipping-2026/) **[secondary]**.
- TikTok Shop: dropshipping allowed (as of April 2026 per sources) if compliant; every seller-fulfilled order must show an "In Transit" carrier scan within 2 business days and be delivered in about 6 business days; >10% late dispatches risks enforcement; cross-border direct-mail orders limited to a carrier whitelist from Feb 1, 2026; US seller setup reportedly requires a US business entity/address — [Shoplazza](https://www.shoplazza.com/blog/tiktok-shop-policy-update); [LZ Dropshipping](https://www.lzdropshipping.com/tiktok-shop-dropshipping-policy-2026-what-ecommerce-sellers-must-know/); [HereWeShip](https://hereweship.com/en/tiktok-shop-shipping-2026-meet-2-day-dispatch-sla/) **[secondary/dropshipping vendors, with a commercial interest; TikTok official policy not verified]**.

### Inferences
- A 2-day dispatch/6-day delivery SLA is incompatible with typical long-transit overseas dropshipping unless using local warehouses/agents.
- Etsy/eBay are realistic only with original-design POD (Etsy) or authorized wholesale suppliers (eBay).

### Gaps
- Official policy pages for all four marketplaces were inaccessible; Israel-based seller eligibility per marketplace (e.g., TikTok Shop availability) not verified.
- No data on organic traffic volume/conversion available to new sellers on these marketplaces.
