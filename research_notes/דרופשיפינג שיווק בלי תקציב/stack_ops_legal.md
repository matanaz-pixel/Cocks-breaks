# Lowest-cost operational stack for global dropshipping from Israel (state as of Oct 2026): platform, payments, suppliers, cash flow, legal/tax, shipping

**Method and reliability caveat (read first).** WebFetch was blocked by the network egress proxy for every primary domain I tried (shopify.com, help.shopify.com, stripe.com, federalregister.gov, ghy.com, adircpa.com, preneuriat.com, cs-cart.com). All findings below therefore come from WebSearch result summaries (title + snippet-level synthesis), not from reading the full primary pages. Primary-source URLs (Federal Register, EU Commission, Consilium, PayPal, Etsy, Wix support, Shopify pages) are cited where the search surfaced them, but figures should be re-verified on the official page before the final report states them as fact. Items marked **[ANECDOTAL]** come from blogs, forums or vendor marketing; **[VENDOR]** marks supplier or app self-published claims; **[UNVERIFIED]** marks things I could not source.

---

## 1. Store platforms: real costs, limits, and the cheapest credible path to a live store

### Takeaway
Shopify is the default and most beginner-friendly choice. It costs about $1/month for the first 3 months via a promo (then $39/month Basic monthly, or $29/month if billed annually), and it adds a 2% third-party transaction fee on every sale because Shopify Payments does not exist in Israel. The truly cheapest credible launches are (a) a marketplace listing (eBay or Etsy POD) at $0 upfront but with strict dropshipping rules, or (b) WooCommerce on ~$3 to 4/month hosting. Ecwid Free is limited to 5 products, and Square Online is not a realistic option from Israel (**[UNVERIFIED]**, not researched).

### Cited Findings
**Shopify**
- Shopify offers a short free trial (3 days) and then $1/month for the first 3 months on Basic, Grow or Advanced (not Plus). The promo requires monthly billing, and switching to annual billing during the promo forfeits the $1 rate. The same source says the promo ran through Black Friday if started in Sept 2026. — [Website Builder Expert](https://www.websitebuilderexpert.com/ecommerce-website-builders/shopify-pricing/), [PageFly](https://pagefly.io/blogs/shopify/shopify-1-dollar), [BSS Commerce](https://bsscommerce.com/shopify/shopify-3-months-for-1-dollar/) (third-party summaries; official page not fetchable)
- 2026 list prices (search summary): Basic $39/month monthly or $29/month billed annually; Grow $105 monthly or $79 annual; Advanced $399 monthly or $299 annual; Starter $5/month; Plus from $2,300/month. — [Website Builder Expert](https://www.websitebuilderexpert.com/ecommerce-website-builders/shopify-pricing/), [Shero Commerce](https://sherocommerce.com/blogs/insights/shopify-pricing)
- Shopify Starter ($5/month): no custom domain (Shopify subdomain), no themes or website builder, no multi-page storefront, no discount codes or abandoned-cart recovery. It is mainly for selling in messages and social. Not a credible dropshipping storefront. — [Shopify Starter plan reviews, e.g. ecomheroes](https://ecomheroes.dev/blogs/shopify-help/shopify-starter-plan), [AdsX](https://www.adsx.com/blog/shopify-starter-plan-five-dollars-review)
- Shopify third-party transaction fee when you do not use Shopify Payments: Basic 2.0%, Grow 1.0%, Advanced 0.6% (Plus 0.2%), on top of the gateway's own fees. Israel has no Shopify Payments, so every Israeli store pays this. — [Shopify Israel blog / community summaries via search](https://www.shopify.com/il/blog/best-payment-gateways), [Shopify Israel payment gateways](https://www.shopify.com/il/blog/payment-gateway-comparison)

**Wix eCommerce**
- Plans (search summary of 2026 pricing, billed annually): Light $17, Core $29 (unlimited products, 0% Wix transaction fees), Business $39, Business Elite $159; one source calls Business Basic $27/month. Prices vary between sources. — [Tooltester](https://www.tooltester.com/en/reviews/wix-review/prices/), [Website Builder Expert](https://www.websitebuilderexpert.com/website-builders/wix-pricing/), [CostBench](https://costbench.com/software/ecommerce/wix-ecommerce/)
- Wix Payments is not available in Israel. Wix does list local providers for Israel: PayPal, Max by Hyp, Isracard, PayPlus, Bit, Meshulam/Grow, Pelecard, Tranzila, Cal, iCredit. — [Wix Studio forum "Is Wix Payments available in Israel"](https://forum.wixstudio.com/t/is-wix-payments-available-in-israel-and-mexico/58291), [Wix: Available Payment Providers in Your Country](https://www.wix.com/payments/payment-gateways), [Wix support: Grow](https://support.wix.com/en/article/connecting-grow-by-meshulam-as-a-payment-provider)
- Wix is weak for dropshipping automation relative to Shopify and DSers/CJ integrations. **[UNVERIFIED]**, my judgment, not sourced.

**WooCommerce**
- Software is free. Hosting from about $2 to 4/month (Hostinger managed WooCommerce $3.99/month; HostArmada $1.99/month on a 3-year term), domain ~$15/year. Search summaries give a realistic beginner range of $100 to 500 for the first year. — [Hostinger](https://www.hostinger.com/tutorials/best-dropshipping-website-builders/), [Cybernews](https://cybernews.com/best-web-hosting/best-cheap-woocommerce-hosting/), [AutoDS WordPress guide](https://www.autods.com/blog/dropshipping-tips-strategies/wordpress-dropshipping/)
- Payment gateways for WooCommerce in Israel are the same local-gateway situation (Stripe unsupported). **[UNVERIFIED]**, no direct source on WooCommerce-specific plugin availability.

**Ecwid**
- Free plan: 5 physical products only (no digital goods), Instant Site one-page store, cannot edit SEO meta titles or descriptions. — [Tooltester Ecwid pricing](https://www.tooltester.com/en/reviews/ecwid-review/pricing/), [Ecommerce Paradise](https://ecommerceparadise.com/ecwid-review-2026-pros-cons-pricing-and-who-its-best-for/)
- Ecwid has a dedicated "Payment options for Israel" help page. — [Ecwid support](https://support.ecwid.com/hc/en-us/articles/360003055219-Payment-options-for-Israel)

**Marketplaces**
- **Etsy**: Etsy Payments is available to sellers in Israel, ILS is supported, but PayPal is not available to Israeli sellers enrolled in Etsy Payments. Etsy only allows dropshipping with creative input: original designs made by a disclosed production partner, POD with your own designs through integrated partners, or craft supplies. Reselling generic AliExpress items violates the Creativity Standards. — [Etsy Countries Eligible for Etsy Payments](https://help.etsy.com/hc/en-us/articles/115015710408-Countries-Eligible-for-Etsy-Payments), [Etsy Israel sell page](https://www.etsy.com/il-en/sell), [Printify: how to dropship on Etsy](https://printify.com/blog/how-to-dropship-on-etsy/), [Dropship.io](https://www.dropship.io/blog/etsy-dropshipping)
- **eBay**: Israel is a seller region under eBay Managed Payments (2.5% currency conversion fee shown); payout options are bank account or Payoneer. eBay's policy allows dropshipping only from a wholesale supplier and explicitly prohibits buying from another retailer or marketplace and shipping to the customer. You remain seller of record. — [AutoDS eBay payment processor](https://www.autods.com/blog/ebay-payment-processor/), [AutoDS eBay dropshipping policy](https://www.autods.com/blog/ebay-dropshipping-policy/), [ZIK Analytics](https://www.zikanalytics.com/blog/ebay-dropshipping-policy/)
- **Amazon**: Amazon has an Israel global-selling program and allows dropshipping only if you are the seller of record (your name on packing slips, you handle returns, no third-party supplier branding). Retail-arbitrage dropshipping is the usual cause of suspensions. Amazon also needs identity docs (notarized English translation if Hebrew-only). Amazon seller fees and subscription are not covered in these notes. — [Amazon Sell from Israel](https://sell.amazon.com/global-selling/israel), [Amazon Seller Central drop shipping policy discussions](https://sellercentral.amazon.com/seller-forums/discussions/t/e4d2d60a1df3b405ee97e91e1d83ad90), [EcomEngine](https://www.ecomengine.com/blog/amazon-drop-shipping-policy)
- **TikTok Shop**: Israel is not among the ~21 markets where a local seller can open a shop (as of Aug 2026); you cannot officially register from Israel. — [PhotonPay](https://www.photonpay.com/hk/blog/article/tiktok-shop-available-countries?lang=en), [DPL Company](https://dpl.company/countries-with-access-to-tiktok-shop-seller-center/)
- Hebrew accountant sources warn **[ANECDOTAL]** that dozens of eBay/Amazon dropshipping seller accounts were closed recently and that dropshipping on those platforms can lead to account closure without notice. — [search summary of Hebrew results, e.g. dropshippingpro.co.il](https://dropshippingpro.co.il/so-what-is-dropshipping/)

### Inferences
- Cheapest credible own-store path: Shopify Basic on the $1/month promo (monthly billing) + free DSers or CJ app + local Israeli gateway (see Section 2). Out-of-pocket for months 1 to 3 is roughly $3 for the plan plus a domain (~$10 to 15, optional at first because Shopify gives a myshopify.com subdomain), plus any gateway setup/monthly fee. Month 4+ jumps to $39/month (or $29/month annual), plus the 2% third-party fee.
- Cheapest $0 path: Etsy (POD with your own designs via Printify/Printful free plans) or eBay (only with a genuine wholesale/dropship supplier). Neither fits "AliExpress to customer" dropshipping.
- Ecwid Free at 5 products is a test-only sandbox, not a business.
- TikTok Shop selling is blocked from Israel; Amazon needs a seller-of-record setup that conflicts with white-label-less China dropshipping. Treat both as not-for-beginner-zero-budget.
- WooCommerce is cheaper per month but costs more of the beginner's time and has more setup risk (security, plugins, speed). My judgment.

### Gaps
- Official shopify.com pricing and promo terms could not be fetched. The $1 promo has been a recurring offer; confirm current end date and whether Israel is eligible (Shopify Israel pages exist, which suggests yes, but not directly confirmed).
- Etsy, eBay and Amazon seller fees and registration requirements for Israeli individuals were not researched in depth.
- No source found on Square Online availability in Israel (likely unavailable; unverified).
- Wix Business Basic vs Core naming conflict across sources.

---

## 2. Payments for Israeli merchants selling globally

### Takeaway
Shopify Payments is not available in Israel and Stripe does not onboard Israeli-registered businesses. A zero-budget Israeli seller realistically needs (1) an Israeli gateway (Grow/Meshulam, Hyp, PayPlus, Tranzila, Cardcom, etc.) connected to Shopify, and/or (2) PayPal Business (works in Israel but with high cross-border fees and a 21-day new-seller hold), with Payoneer as a receiving account where the gateway supports it. Foreign-entity workarounds (e.g. a US LLC to get Stripe) exist but cost money and carry tax/legal complexity.

### Cited Findings
**Shopify Payments / Stripe**
- Shopify's official Shopify Payments list covers about 40 countries; Israel is not on it. Israeli stores must use a third-party gateway. — [Shopify community: Is Shopify Payments available in Israel](https://community.shopify.com/t/is-shopify-payments-available-in-israel/287393), [preneuriat.com (not fetched, via search)](https://preneuriat.com/blog/shopify-payment-gateways-israel), [Shopify Help: Managed Markets requirements](https://help.shopify.com/en/manual/international/managed-markets/requirements-and-considerations)
- Israel is not on Stripe's supported-countries list (checked July 2026 per source); you cannot open a Stripe account on an Israeli osek or company. Some Israeli businesses use a foreign entity (e.g. Delaware) to access Stripe. — [Adir CPA "Stripe in Israel: What Works in 2026"](https://adircpa.com/en/guides/stripe-israel-tax), [Stripe global availability](https://stripe.com/global), [Stripe Israel step by step, isitavailablein.com](https://isitavailablein.com/how-to-use/stripe/israel)
  - Conflict: another search summary claims Israelis "can open a Stripe account by following certain requirements," which looks like a low-quality aggregator claim. Treat as unreliable. — [LinkedIn article "How to use Stripe in Israel"](https://www.linkedin.com/pulse/how-use-stripe-israel-mazino-oyolo-wn2rf)
- Stripe Atlas (Delaware C-corp or LLC) costs a one-time $500 (then ~$100/year renewal) and is open to non-US founders. This is the common but costly workaround; tax and compliance implications for an Israeli resident were not researched. — [Stripe Atlas cost, sparklaun.ch](https://sparklaun.ch/compare/stripe-atlas), [Rho](https://www.rho.co/blog/stripe-atlas-alternatives)

**Israeli gateways on Shopify**
- Shopify's Israel gateway list includes Grow, Hyp, PayPlus, Tranzila PayTech and iCount; the Shopify App Store also lists Cardcom. Hebrew guides say PayMe, Z-credit, Pelecard, CreditGuard and others can connect via iCount. — [Shopify Israel: best payment gateways](https://www.shopify.com/il/blog/best-payment-gateways), [Shopify payment gateways](https://www.shopify.com/payment-gateways), [Q-Biz Hebrew guide](https://q-biz.co.il/shopify-israel-payment-providers/)
- Indicative monthly entry plan fees **[ANECDOTAL/aggregator]**: Tranzila NIS 29 to 99, Cardcom NIS 39 to 129, Meshulam/Grow NIS 0 to 59. Per-card fees roughly 1.5% to 3%; Tranzila charges more for international cards. — [Daniel Mashkov comparison](https://danielmashkov.com/insights/israeli-payment-gateways-comparison), [Shopify vs WooCommerce in Israel](https://danielmashkov.com/insights/shopify-vs-woocommerce-israel-2026)
- Pelecard on Wix: transaction fees starting from 1.50%, all currencies. — [Wix support: Pelecard](https://support.wix.com/en/article/connecting-pelecard-as-a-payment-provider)
- Israeli gateways typically require an Israeli business registration (osek patur/murshe or company) and an Israeli bank account. **[UNVERIFIED]**, standard practice but not sourced here. Whether each gateway accepts foreign-issued cards in USD/EUR/GBP and settles in foreign currency, and its chargeback/reserve terms, was not found.
- Shopify Subscriptions apps only support PayPal Express for Israeli stores. — [Ongoing Payments help](https://intercom.help/ongoingpayments/en/articles/8105193-ongoing-shopify-subscriptions-for-israel-which-payment-gateways-can-i-use)

**PayPal**
- PayPal seller fees for Israel (international): 3.40% + fixed for EEA buyers, 4.69% + fixed for UK buyers, 5.39% + fixed for all other markets. Chargeback fee NIS 75. Fee cap NIS 320 international. — [PayPal Israel seller fees via search (paypal.com business-fees pages)](https://www.paypal.com/ee/business/paypal-business-fees), [Shopify Israel PayPal fees](https://www.shopify.com/il/blog/paypal-fees)
  - Another source (general, not Israel-specific) gives 3.49% + fixed for checkout, 2.99% for cards, +1.5% cross-border, and a 3% to 4% currency conversion spread. — [Shopify Israel gateway comparison](https://www.shopify.com/il/blog/payment-gateway-comparison), [Airwallex](https://www.airwallex.com/en-au/blog/how-much-does-paypal-business-charge-for-international-payments)
- Withdrawals: Israeli PayPal accounts can withdraw to Israeli bank accounts or cards in ILS only; 3 to 5 days; NIS 8 fee if under NIS 1,000; minimum NIS 40. — [LeverageIT](https://leverage.it/withdraw-paypal-to-israeli-bank-accounts/), [illuminea](https://illuminea.com/how-paypal-really-works-for-israelis-and-shekels/) **[ANECDOTAL, may be dated; verify]**
- New PayPal sellers' initial payments may be held up to 21 days; adding tracking or proof of fulfillment can release funds, typically within 24 hours of delivery confirmation. — [PayPal: New PayPal sellers, payments on hold](https://www.paypal.com/us/cshelp/article/new-paypal-account-%E2%80%93-payments-on-hold-and-accessing-your-money-quicker-help848), [PayPal funds availability](https://www.paypal.com/us/brc/article/funds-availability)
- Israel PayPal User Agreement updated 6 July 2026 (the document that contains reserve/hold terms for Israeli users; I did not read its contents). — [PayPal IL user agreement PDF](https://www.paypalobjects.com/marketing/ua/OA/useragreement-full/en-IL-070626.pdf)
- Hebrew guide **[ANECDOTAL]**: connecting PayPal directly is possible, but a card gateway is better for conversion. — [Hebrew results summary](https://q-biz.co.il/shopify-israel-payment-providers/)

**Payoneer / Wise / Paddle**
- Payoneer is Israel-founded and works as a receiving account; funds can be withdrawn to Payoneer via a third-party gateway partner on Shopify. Card fees up to ~3%; no monthly/setup fee. Whether Payoneer Checkout specifically works for Israeli Shopify stores was asked on the Shopify community without a confirmed answer in my results. — [Payoneer Shopify payouts guide](https://www.payoneer.com/resources/general-payments/shopify-payouts-withdrawal/), [Shopify community thread](https://community.shopify.com/c/payments-shipping-and/is-payoneer-checkout-available-in-israel-for-shopify/m-p/2032376)
- eBay Managed Payments pays out to bank or Payoneer for Israeli sellers (see Section 1).
- Wise: available to Israeli residents for personal transfers; one summary says Wise does not accept Israeli businesses and the Wise Multi-Currency Card is not available to Israel residents. Confidence in this is **low**, it is a search summary only. — [Payset on Wise Israel](https://www.payset.io/wise-israel/), [Exiap](https://www.exiap.com/guides/using-the-wise-card-in-israel)
- Paddle/Lemon Squeezy/FastSpring (merchant-of-record) are built for SaaS/digital goods, not physical goods or dropshipping. Paddle is described as supporting Israeli sellers, but only for digital products. — [eBrands: SaaS MoRs don't fit physical goods](https://www.ebrands.com/blog-posts/why-saas-merchant-of-record-doesnt-work-for-physical-goods), [Lemon Squeezy MoR docs](https://docs.lemonsqueezy.com/help/payments/merchant-of-record)
- Etsy Payments for Israeli sellers excludes PayPal (see Section 1).

**Chargeback thresholds**
- Visa VAMP: merchant "Excessive" threshold 1.5% (dispute + fraud ratio) from April 1, 2026 (US merchants). Mastercard ECM: 100 chargebacks or 1.5% in a month; HECM at 300 or 3%. Most merchants should stay below 1%, ideally under ~0.65%. — [Chargeback.io](https://www.chargeback.io/blog/average-e-commerce-chargeback-rate), [Chargeflow thresholds](https://www.chargeflow.io/blog/chargeback-threshold-limits), [cside VAMP 2026](https://cside.com/blog/vamp-2026-merchant-playbook)
- Israeli Tax Authority scrutinizes foreign bank accounts and digital wallets (Payoneer etc.); unreported funds abroad can lead to prosecution. — [Krisi Tax (Hebrew)](https://krisitax.com/%D7%97%D7%A9%D7%91%D7%95%D7%9F-%D7%91%D7%A0%D7%A7-%D7%91%D7%97%D7%95%D7%B4%D7%9C-%D7%90%D7%95-%D7%9B%D7%A1%D7%A3-%D7%91%D7%9E%D7%9B%D7%A9%D7%99%D7%A8-%D7%A4%D7%99%D7%A0%D7%A0%D7%A1%D7%99/) **[law firm blog]**

### Inferences
- Minimum viable payment stack: PayPal Business (free to open) + one Israeli gateway with international card acceptance (Grow/Meshulam entry plan is the cheapest per the aggregator). Combined drag on each sale: Shopify 2% + gateway ~1.5 to 3% (or PayPal ~5.4% on non-EEA/UK buyers). That is 4% to 7.5% before supplier cost. Using PayPal for US customers (5.39% + fixed) with 2% Shopify fee leaves a thin margin on a $20 to 30 item.
- Payouts to Israel arrive in ILS only via PayPal, so FX conversion cost sits on top of the 3% to 4% spread.
- Dropshipping is a classic high-dispute category (slow delivery, "not as described"), so the 1.5% Visa/Mastercard thresholds and PayPal's hold and reserve behaviour are the real operating risks for a new Israeli account. No source on Israeli-gateway reserves, which is a gap.
- Stripe via Atlas is realistic but is NOT zero-budget ($500 + compliance + Israeli tax reporting of the foreign company, CFC/residency issues unresearched). Beginners should skip it until revenue justifies it.

### Gaps
- Fees, settlement currencies, payout schedules, reserves and chargeback policies for Grow, Hyp, PayPlus, Tranzila, Cardcom, PayMe: not found in any citable source (only aggregator ranges).
- Whether an individual osek patur can open an account with each gateway, and minimum documentation: not found.
- PayPal reserve/hold rules specific to Israeli accounts (beyond the general 21-day new-seller hold): not found; the IL user agreement was not read.
- Payoneer Checkout availability for Israeli Shopify merchants: unconfirmed.
- Wise business availability for Israeli residents: conflicting/low-confidence.

---

## 3. Suppliers and fulfillment (free plans, shipping times, quality, warehouses)

### Takeaway
For a zero-capital beginner the lowest-cost supplier stack is DSers (AliExpress, free plan) and/or CJ Dropshipping (free, with US/EU warehouses and a CJPacket tracked line), plus Printify/Printful free plans for POD. Zendrop's free plan allows 50 orders/month; Eprolo is free; Spocket, Syncee, BigBuy and AutoDS cost money up front. Real shipping from China is 7 to 20+ days and now carries US/EU duties that change who pays.

### Cited Findings
- **CJ Dropshipping** [VENDOR/review sites]: free to join, no subscription or MOQ; real costs are product + shipping + optional fees. US warehouses (New Jersey, California) give ~3 to 7 day domestic delivery; China to US via CJPacket typically 7 to 17 days. US warehouse storage: free window, then $0.63/CBM/day (days 61 to 120), $1.26/CBM/day (days 121 to 180); handling $0.50/order up to 1 kg. 5 daily sourcing requests on the free plan. — [Revenue Geeks](https://revenuegeeks.com/software/cjdropshipping), [Daily Fulfill: promise vs reality](https://www.dailyfulfill.com/cj-dropshipping-shipping-times-promise-vs-reality-2026/), [eDesk](https://www.edesk.com/blog/cj-dropshipping-everything-to-get-started/), [Spocket (competitor) overview](https://www.spocket.co/blogs/cjdropshipping-overview)
- **DSers / AliExpress**: free plan = 1 store connection, up to 3,000 products, one-click importer, bulk ordering (one source says up to 3 stores on Basic; sources conflict on store count). Paid: Advanced $19.90/month (20,000 products, 10 stores), Pro $49.90/month, Enterprise $499/month; 14-day trial on paid tiers; 20% annual discount. — [Revenue Geeks DSers pricing](https://revenuegeeks.com/software/dsers/pricing), [App Store Research](https://appstoreresearch.com/shopify-app-reviews/dsers), [Seller Tools Hub](https://sellertoolshub.com/tools/dsers)
- **AliExpress shipping options 2026** [guide sites, vary by lane]: Standard 15 to 45 days; Premium 7 to 15 days; Choice 5 to 12 days (curated items); ePacket 10 to 20 days (12 to 20 to US historically, availability affected by 2025-26 US tariff changes). — [SecretAli](https://secretali.com/guide/aliexpress-shipping-time-2026), [AliExpress shipping options compared](https://news.astools.app/en/blog/aliexpress-shipping-options-compared-2026)
- **Zendrop**: Free (forever, up to 50 orders/month per one comparison), Pro $49/month ($399/year), Plus $79/month ($549/year). — [Branvas](https://branvas.com/blogs/news/spocket-vs-zendrop), [Avada](https://avada.io/blog/spocket-vs-zendrop/)
- **Spocket**: no permanent free plan; Starter $39.99/month, Pro $59.99 ($24 annual), Empire $99.99, Unicorn $299.99; trial only. — [Product Upload comparison](https://productupload.co/resources/comparison/product-upload-vs-spocket), [Avada](https://avada.io/blog/spocket-vs-zendrop/)
- **Eprolo**: core platform free; branding add-on $19.90/year or $99 package [VENDOR/competitor-comparison text]. — [Eprolo vs Zendrop](https://eprolo.com/zendrop-alternatives)
- **Syncee**: has a free plan; paid from ~$39.9/month. **BigBuy** (EU supplier): roughly EUR 69 to 120/month plus ~EUR 90 registration fee per the same summary [low confidence]. — [Bettamax alternatives](https://bettamax.com/best-spocket-alternatives/), [Saasworthy comparison](https://www.saasworthy.com/compare/spocket-co-vs-doba-vs-zendrop?pIds=956%2C7472%2C11883)
- **AutoDS**: no permanent free plan; plans from ~$19.90/month on Shopify (Import plan $26.90/month, up to 200 products per one summary), trial about $1 for 14 days (or $0.99 for 3 days, sources vary). Cartly pricing: not found. — [Affmaven](https://affmaven.com/autods-pricing/), [Revenue Geeks AutoDS trial](https://revenuegeeks.com/software/autods/free-trial)
- **Printful**: free plan, pay per order; Growth plan $24.99/month (free if you reach $12,000 sales in 12 months). Production 2 to 5 business days. **Printify**: free plan; Premium rose from $29 to $39/month on Feb 17, 2026 (annual $299/year unchanged) for up to 20% discount. — [Dodropshipping pricing comparison](https://dodropshipping.com/printful-vs-printify-pricing/), [Branvas Printful vs Printify](https://branvas.com/blogs/news/printful-vs-printify), [Chayaani Printify 2026](https://chayaani.com/blog/printify-pricing-fees-guide-2026)
- Printify's network status page notes parcels to Israel were suspended in June 2025 due to escalation; Printful treats Israel as worldwide shipping with erratic transit. This concerns shipping TO Israel (e.g. samples), not your customers abroad. — [Printify network status](https://printify.com/network-fulfillment-status/), [PodVector on Printful shipping](https://podvector.ai/articles/printful/shipping/printful-worldwide-shipping-times-costs-and-what-to-expect)
- Sample orders to yourself in Israel may be disrupted; Etsy/POD sellers should test via a friend abroad or a customer-facing mockup. **[Inference]**.
- Product quality/refund issues: the supplier-review sites summarized above largely do not give refund-policy or Trustpilot data for CJ in my results. **[Gap]**.

### Inferences
- Best beginner zero-capital stack: Shopify (promo) + DSers free (AliExpress Choice/Premium shipping where available) or CJ free (CJPacket, US/EU warehouse where stock exists). Use Printify/Printful free for POD if going Etsy or branded merch.
- Everything shipped from China to US now carries duty exposure (Section 5); US-warehouse stock via CJ avoids import duty at customer level but requires paying for inventory upfront or CJ's stock-based fulfillment, which breaks zero-capital.
- Spocket, Syncee (paid tiers), BigBuy, AutoDS are not zero-budget. AutoDS is automation, not a supplier.

### Gaps
- No primary-source (supplier official) pricing/shipping pages fetched.
- Refund, defect rates and Trustpilot data not found.
- Zendrop's free plan limits (50 orders/month) come from a comparison blog; verify.
- AU, CA lane transit times per supplier not found.

---

## 4. Cash flow reality: working capital, refunds, chargebacks, payment holds

### Takeaway
With dropshipping you receive the customer's money first, then buy from the supplier, so the true minimum is small, but the gap is: (a) payment holds (PayPal up to 21 days for new sellers), (b) paying supplier before funds are released, (c) refunds/chargebacks plus processor fees that are not returned. Realistically a zero-budget operator needs a few hundred dollars of buffer plus ad/test spend, which was not within the scope of sourced data.

### Cited Findings
- New PayPal sellers face holds of up to 21 days on initial payments; tracking uploads can release the funds within about 24 hours of delivery confirmation. — [PayPal: new seller holds](https://www.paypal.com/us/cshelp/article/new-paypal-account-%E2%80%93-payments-on-hold-and-accessing-your-money-quicker-help848)
- PayPal Israel chargeback fee: NIS 75 per chargeback. — [PayPal IL fee summary](https://www.paypal.com/ee/business/paypal-business-fees)
- Chargeback programs: Visa 1.5% and Mastercard 100 chargebacks or 1.5% thresholds (see Section 2). Visa VAMP penalty structure of $8 per disputed or fraudulent transaction cited by one source. — [cside VAMP 2026](https://cside.com/blog/vamp-2026-merchant-playbook)
- FTC Mail Order Rule: if you cannot ship within the promised time (or within 30 days where none is stated) you must notify and offer cancel/refund. — [FTC press release on Internet Commerce and Mail-Order Rule](https://www.ftc.gov/news-events/news/press-releases/1999/11/internet-commerce-mail-order-rule), [NatLawReview](https://natlawreview.com/article/ftc-settlement-serves-reminder-mail-order-rule-compliance-obligations)
- Shopify and gateway fees are non-refundable on refunds in typical processor practice. **[UNVERIFIED]**, no source gathered.

### Inferences
- Minimum working capital (my estimate, NOT sourced): enough to buy 1 to 3 orders from the supplier before payout, plus sample orders ($20 to 60 each) and a refund buffer. With PayPal holds, expect supplier costs to be paid from your own funds or card for the first 21 days. Pre-fund from a personal card; consider setting supplier payment via a card with chargeback protection.
- Each chargeback costs you the order value + product cost (already shipped or paid) + NIS 75 (PayPal); a 1.5% rate on a low-volume store can be hit with 1 to 2 disputes, so slow shipping is existential.
- Ad spend is the largest hidden capital need, but it is outside this note's scope.

### Gaps
- No sourced "minimum working capital" figure; no data on Israeli gateways' reserve policies; no data on actual refund rates for China-shipped dropshipping.

---

## 5. Legal and tax realities (US, EU, UK, Israel; platform rules; consumer law)

### Takeaway
The era of duty-free low-value China parcels is over: US de minimis is permanently suspended (all countries since Aug 29, 2025; codified June 2026), EU charges EUR 3 per tariff line on parcels up to EUR 150 from July 1, 2026, and the UK plans to end its GBP 135 duty relief by March 2029. Israeli individuals selling goods located outside Israel to foreign customers are VAT-exempt per Hebrew accountant sources, but income tax and National Insurance still apply, and a business registration is practically required.

### Cited Findings
**US**
- US de minimis was suspended for China/HK on May 2, 2025 and for all countries on Aug 29, 2025; suspension was continued Feb 20, 2026; on June 24, 2026 CBP made it indefinite via interim final rules. From Feb 28, 2026 postal shipments use the ad valorem duty method only. — [CRS In Focus](https://www.congress.gov/crs-product/IF12891), [Federal Register 2026-12670 (indefinite suspension, modes other than postal)](https://www.federalregister.gov/documents/2026/06/24/2026-12670/indefinite-suspension-of-the-de-minimis-exemption-for-merchandise-arriving-through-all-modes-other), [GHY: end of de minimis for China](https://www.ghy.com/trade-compliance/us-de-minimis-exemption-ends-for-china-low-value-imports/)
- Supreme Court ruled on Feb 20, 2026 (6-3) that IEEPA does not authorize tariffs, cutting China's effective rate from about 36.8% to about 21.2% per one source. The administration then used Section 122 (10%, raised to 15%, effective Feb 24 for 150 days); the Court of International Trade struck it down May 7, 2026 (on appeal); Section 122 expired July 24, 2026. — [Wiley](https://www.wiley.law/alert-SCOTUS-Invalidates-Use-of-IEEPA-for-Trade-Tariffs), [Brookings](https://www.brookings.edu/articles/brookings-experts-on-the-supreme-courts-tariff-decision/), [Skadden](https://www.skadden.com/insights/publications/2026/05/us-trade-court-strikes-down-section-122-tariffs), [China Briefing](https://www.china-briefing.com/news/supreme-court-tariff-ruling-china-impact/)
- Since July 24, 2026 a new Section 301 "forced labor" action imposes 10% to 12.5% (China-origin: 12.5%) on 60 economies; postal shipments from China now pay standard duties (MFN + Section 301 + 232 where applicable). — [Zonos US tariff updates](https://zonos.com/us-tariff-updates), [Ginger Control](https://gingercontrol.com/blog/section-122-tariffs-explained), [US Tariff Rates: China](https://ustariffrates.com/tariff-rates/china) (aggregator sources; verify with CBP/USTR)
- US sales tax: Wayfair economic nexus applies to foreign sellers with typical thresholds of $100,000 or 200 transactions; no exemption for being based abroad. Marketplace facilitator laws shift collection to Etsy/eBay/Amazon. — [Commenda](https://www.commenda.io/blog/sales-tax-thresholds), [Beancount.io](https://beancount.io/blog/2026/04/23/wayfair-law-economic-nexus-sales-tax-guide)
- FTC Mail Order Rule applies to dropshippers: ship by the promised date (or 30 days if none stated; 50 days if paying with credit) or notify and offer refund. — [FTC](https://www.ftc.gov/news-events/news/press-releases/1999/11/internet-commerce-mail-order-rule), [Purchy summary](https://www.purchy.ai/blog/ftc-mail-order-rule-30-day-shipping-2026)

**EU**
- From July 1, 2026 a temporary EUR 3 customs duty applies per tariff subheading (HS code) in each consignment up to EUR 150, until July 1, 2028; it is added on top of import VAT; owed by the declarant (importer or IOSS intermediary). — [European Commission guidance](https://taxation-customs.ec.europa.eu/news/guidance-and-legal-text-temporary-flat-fee-low-value-imports-which-will-apply-until-1-july-2028-2026-06-08_en), [Council of the EU press release](https://www.consilium.europa.eu/en/press/press-releases/2025/12/12/customs-council-agrees-to-levy-customs-duty-on-small-parcels-as-of-1-july-2026/), [Eurofiscalis](https://www.eurofiscalis.com/en/eu-3-euro-customs-duty-small-parcels/), [VATAI](https://www.vatai.com/blog/eu-3-euro-customs-duty-small-parcels-2026)
- IOSS applies to B2C consignments of EUR 150 or less; non-EU sellers need an EU-based IOSS intermediary (fiscal representative). Without IOSS, VAT is collected from the customer at delivery. — [Hellotax IOSS for non-EU sellers](https://hellotax.com/blog/ioss-for-non-eu-sellers/), [Dodropshipping VAT guide](https://dodropshipping.com/cross-border-vat-eu-dropshipping/), [EAS Project](https://help.easproject.com/hc/en-gb/articles/5371372277791-EU-VAT-Registration-for-Non-EU-Sellers-2026-B2C-Compliance-Cheat-Sheet)
- GDPR Article 27: non-EU controllers offering goods to EU residents must designate an EU representative; UK GDPR has an equivalent. Whether small stores without establishment realistically enforce this, and Israel's adequacy status, were not established in the sources I found. — [GDPR Local](https://gdprlocal.com/what-is-an-article-27-representative/), [IAPP](https://iapp.org/news/a/eu-representative-on-how-to-operationalize-article-27-of-the-gdpr)

**UK**
- Currently consignments up to GBP 135 are duty-free but attract import VAT collected at point of sale (since 2021). The Autumn Budget 2025 announced the end of the GBP 135 duty relief by March 2029; consultation closed March 6, 2026. — [Menzies](https://www.menzies.co.uk/uk-to-remove-de-minimis-threshold-by-2029/), [VAT Update](https://www.vatupdate.com/2026/03/15/uk-to-end-135-customs-duty-exemption-for-low-value-imports-by-2029blish-on-mar/), [KPMG UK](https://kpmg.com/uk/en/insights/tax/tmd-autumn-budget-custom-tax.html)

**Israel: tax status**
- Osek patur (VAT-exempt dealer) ceiling for 2026: NIS 122,833 annual turnover (older sources cite about NIS 100,000 or NIS 107,692 for 2023). Osek patur does not charge VAT or deduct input VAT but still files annual income tax (Form 1301), pays advances, and pays National Insurance. — [Adir CPA freelance guide](https://adircpa.com/en/guides/freelance-in-israel), [Slate guide to osek patur](https://www.slate.co.il/en/guides/osek-patur), [CWS Israel](https://www.cwsisrael.com/freelancer-shield-vs-osek-patur/)
- Hebrew accountant sources: where the goods never pass through Israel and the buyer is a foreign resident, the sale is VAT-exempt; a person who buys abroad and sells to Shopify/eBay customers may not be classed as patur/murshe for VAT; but an Israeli resident remains liable for income tax on worldwide income, and online trade is not outside the law even if goods never enter Israel. Income tax and National Insurance treatment is identical for patur and murshe. — [Ziv Shiffer CPA (Hebrew)](https://www.zscpa.co.il/%D7%9E%D7%99%D7%A1%D7%95%D7%99-%D7%A2%D7%9C-%D7%93%D7%A8%D7%95%D7%A4%D7%A9%D7%99%D7%A4%D7%99%D7%A0%D7%92/), [Yaniv Nesher CPA (Hebrew)](https://yncpa.co.il/%D7%93%D7%A8%D7%95%D7%A4%D7%A9%D7%99%D7%A4%D7%99%D7%A0%D7%92-%D7%9E%D7%94%D7%9D-%D7%97%D7%95%D7%A7%D7%99-%D7%94%D7%9E%D7%99%D7%A1%D7%95%D7%99/), [Eyal Raz](https://eyalraz.co.il/droptoisrael-o-pator/), [Krisi Tax](https://krisitax.com/%D7%9E%D7%A1%D7%97%D7%A8-%D7%93%D7%A8%D7%95%D7%A4%D7%A9%D7%99%D7%A4%D7%99%D7%A0%D7%92-%D7%94%D7%99%D7%91%D7%98%D7%99-%D7%9E%D7%A1/) **[accountant blogs, not gov.il; verify with Israel Tax Authority/an accountant]**
- Self-employed National Insurance: 4.47% on income up to NIS 7,703/month and 12.83% above (aggregator summary of 2026 rates); minimum contribution for a non-earning resident NIS 266/month. Income tax brackets (aggregator, 2026): 10% up to NIS 84,120; 14% to 120,720; 20% to 228,000; 31% to 301,200; 35% to 560,280; 47% to 721,560; 50% above. These figures come from calculator/aggregator sites and may not be fully accurate. — [CWS Israel](https://www.cwsisrael.com/freelancer-tax-compliance-in-israel-2026/), [CountryTaxCalc](https://www.countrytaxcalc.com/tax-guides/countries/israel/), [Bituach Leumi guide](https://israellaw.info/articles/bituach-leumi-foreigners-israel)
- Opening an osek patur requires an Israel Tax Authority/VAT registration and National Insurance enrollment. — [Deel](https://www.deel.com/blog/sole-proprietorship-israel/), [CPA.digital](https://cpa.digital/services/exempt/)

**Platform restrictions and disclosure**
- eBay and Amazon require you to be seller of record and ban sourcing from other retailers; Etsy requires creative input (see Section 1). Shopify's own dropshipping acceptance and its policy pages (terms, shipping, refund, privacy) were not retrieved. — see Section 1 citations.

**Consumer law (EU/UK 14-day withdrawal; required pages)**
- No source retrieved in this run for the EU/UK 14-day cooling-off period, required policy pages, or CCPA basics. **[UNVERIFIED]**; standard knowledge is that EU/UK distance-selling rules require a 14-day withdrawal right, clear pre-contract information, and the seller's identity/address, but this must be sourced by the report writer.

### Inferences
- Because US duty now applies to every low-value parcel, a seller shipping from China to the US should either use DDP (delivered duty paid, price-in duty) or accept customer-collected duties at delivery, which drives refusals, chargebacks and poor reviews. Some sources suggest China-origin duty is now 12.5% + MFN + Section 301 list rates (on top for many categories, 7.5% to 25% on Section 301 lists, not verified here).
- For the EU, an IOSS number via an intermediary (a paid service) is practical only after volume; without IOSS, customers pay VAT plus handling fees on delivery, which depresses conversion. The EUR 3 duty is per HS code, so multi-SKU parcels cost more.
- For Israel: register as osek patur (free) before first sale, and keep records. Because the goods never enter Israel, accountants say sales are VAT-exempt either way; however, using an Israeli gateway/bank account means income will be visible to the Tax Authority. Treat all of this as needing a quick accountant check (many offer free first consultations, unverified).
- Foreign sales tax nexus ($100k/200 transactions) is not relevant at launch.

### Gaps
- gov.il / Israel Tax Authority primary pages were not retrieved; Israeli tax guidance is from accountants and aggregators.
- No data on whether Israeli osek patur may sell abroad via foreign gateway without reporting (Payoneer wallets must be reported per Krisi Tax summary; foreign-exchange rules not researched).
- Required policy pages, EU/UK 14-day returns, CCPA/CPRA applicability thresholds, UK VAT registration thresholds for overseas sellers: not researched.
- UK: the GBP 135 VAT regime for non-UK sellers (VAT registration requirement at point of sale for consignments up to GBP 135) was not sourced beyond one line.

---

## 6. Shipping times and customer expectations in 2026; effect on conversion and refunds

### Takeaway
Shipping cost and clarity matter more than raw speed, but long 10 to 30 day transit from China, plus now-visible duties, produce "where is my order" tickets, cancellations and disputes. Tracked, honest delivery windows and fast lanes (CJPacket 7 to 17 days, AliExpress Choice 5 to 12 days, local warehouses 3 to 7 days) are the practical mitigation.

### Cited Findings
- Cart abandonment averages about 70.22% in 2026; 48% abandoned a cart in the last 3 months over delivery issues; 43% abandoned due to slow shipping; 48% abandon when extra shipping/tax costs appear. — [Red Stag Fulfillment](https://redstagfulfillment.com/percentage-of-online-shoppers-abandon-their-cart/), [Drip](https://www.drip.com/blog/cart-abandonment-statistics), [Ringly](https://www.ringly.io/blog/ecommerce-shipping-statistics-2026)
- 74% of customers expect delivery within two days (survey claim); however, a McKinsey-cited survey says 90% will wait 2 to 3 days to avoid shipping costs; delivery speed fell from #1 priority in 2022 to #5 in 2024 behind shipping cost, transparency, flexibility and easy returns; 62% say an accurate delivery date matters more than speed. — [Setubridge](https://www.setubridge.com/ecommerce-shipping-delivery-statistics/), [Shopifreaks on McKinsey](https://www.shopifreaks.com/mckinsey-survey-finds-90-of-consumers-will-wait-2-3-days-for-delivery-to-avoid-shipping-costs-as-e-commerce-growth-slows/), [Portless](https://www.portless.com/blogs/ecommerce-shipping-speed), [Opensend](https://www.opensend.com/post/average-shipping-time-statistics-ecommerce)
- Most customers accept 3 to 5 day shipping when communicated clearly with tracking; frustration is driven by uncertainty. [Practitioner/vendor claims, ANECDOTAL] — [Portless](https://www.portless.com/blogs/ecommerce-shipping-speed)
- CJ: China to US ~7 to 17 days (CJPacket); US warehouse 3 to 7 days. AliExpress: Standard 15 to 45 days, Premium 7 to 15, Choice 5 to 12. — [Daily Fulfill](https://www.dailyfulfill.com/cj-dropshipping-shipping-times-promise-vs-reality-2026/), [SecretAli](https://secretali.com/guide/aliexpress-shipping-time-2026)
- Mid-2025 military escalation disrupted flights and parcel service to Israel (Printify noted a suspension of shipments TO Israel); this relates to inbound logistics for the seller's own samples, not customers abroad. — [Printify network status](https://printify.com/network-fulfillment-status/)

### Inferences
- Show a realistic range (e.g. 7 to 18 business days), provide tracking, and give proactive delay emails; that fits both the FTC Mail Order Rule and reduces "item not received" chargebacks.
- Quoting shipping inclusive of duty/VAT (DDP) removes the "extra costs at delivery" abandonment/refusal driver; it requires price headroom.
- Marketing claims such as "fast shipping" or "delivered in 3 to 5 days" while using China lanes risk consumer-law and chargeback exposure.

### Gaps
- Stats come from vendor/aggregator blog posts that may mix US-only and global data; no country-specific data for UK/EU/CA/AU, and no sourced figure linking delivery time to refund/chargeback rates for dropshipping.
- Real-world delivery time to AU and CA by lane not researched.

---

## Summary table: what to set up (zero-budget order of operations), cost, and what's blocked

| Step | Cheapest option | Minimum cost | Source/status |
|---|---|---|---|
| Legal status | Osek patur (Israel), report income | NIS 0 to register (accountant optional); NI contributions apply | Sec. 5, accountant blogs |
| Store | Shopify Basic via $1/month promo (monthly billing) | ~$3 for 3 months, then $39/month (or $29/month annual) + 2% third-party fee | Sec. 1 |
| Cheaper no-monthly path | Etsy POD or eBay (wholesale supplier only) | $0 upfront; listing/selling fees not researched | Sec. 1 |
| Payments | Israeli gateway (Grow/Meshulam etc.) + PayPal Business | Gateway monthly ~NIS 0 to 129 (aggregator); PayPal 5.39% + fixed on non-EEA/UK cards | Sec. 2 |
| Supplier | DSers free + CJ free; Printify/Printful free for POD | $0 monthly | Sec. 3 |
| Blocked or not practical | Shopify Payments; Stripe (Israeli entity); TikTok Shop seller account from Israel; Paddle/Lemon Squeezy for physical goods; PayPal on Etsy for Israeli sellers; Wix Payments | n/a | Secs. 1 to 2 |
| Risky | PayPal 21-day holds; AliExpress/Amazon/eBay arbitrage dropshipping (account closures); China-to-US/EU parcels with new duties; paid tools (Spocket, AutoDS, BigBuy) not zero-budget | n/a | Secs. 1 to 5 |
