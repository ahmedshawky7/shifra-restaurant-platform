package com.restaurant.platform.infrastructure.config;

import com.restaurant.platform.application.port.out.InvoiceRepositoryPort;
import com.restaurant.platform.application.port.out.OrderRepositoryPort;
import com.restaurant.platform.application.port.out.TableRepositoryPort;
import com.restaurant.platform.domain.model.*;
import com.restaurant.platform.domain.model.enums.*;
import com.restaurant.platform.infrastructure.adapter.out.persistence.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Configuration
public class DataSeeder {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);
    private static final Set<String> WEAK_PASSWORDS = Set.of("123456", "12345678", "admin", "password", "superadmin");

    @Value("${app.seed.enabled:false}")
    private boolean seedEnabled;

    @Value("${app.superadmin.email:}")
    private String superAdminEmail;

    @Value("${app.superadmin.password:}")
    private String superAdminPassword;

    @Bean
    CommandLineRunner initDatabase(UserJpaRepository userRepo,
            OrganizationJpaRepository orgRepo,
            BranchJpaRepository branchRepo,
            MenuCategoryJpaRepository categoryRepo,
            MenuItemJpaRepository itemRepo,
            ItemVariantJpaRepository variantRepo,
            ItemAddonJpaRepository addonRepo,
            RestaurantTableJpaRepository tableRepo,
            OrderRepositoryPort orderRepo,
            TableRepositoryPort tableDomainRepo,
            InvoiceRepositoryPort invoiceRepo,
            PasswordEncoder encoder,
            Environment env) {
        return args -> {
            boolean isProdProfile = env.acceptsProfiles(Profiles.of("prod"));
            boolean isDevProfile = env.acceptsProfiles(Profiles.of("dev"));

            if (isProdProfile && seedEnabled) {
                throw new IllegalStateException(
                        "رفض التشغيل: لا يُسمح بتفعيل APP_SEED_ENABLED=true داخل بيئة الإنتاج (prod).");
            }

            if (userRepo.count() == 0) {
                if (superAdminEmail != null && !superAdminEmail.isBlank()
                        && superAdminPassword != null && !superAdminPassword.isBlank()) {
                    if (isProdProfile && (superAdminPassword.length() < 8
                            || WEAK_PASSWORDS.contains(superAdminPassword.toLowerCase()))) {
                        throw new IllegalStateException(
                                "رفض التشغيل: كلمة مرور السوبر أدمن ضعيفة أو افتراضية في بيئة الإنتاج (يجب ألا تقل عن 8 أحرف).");
                    }
                    UserEntity superAdmin = new UserEntity();
                    superAdmin.setUsername(superAdminEmail.trim());
                    superAdmin.setPassword(encoder.encode(superAdminPassword));
                    superAdmin.setRole(UserRole.SUPER_ADMIN);
                    superAdmin.setFullName("مدير المنصة");
                    superAdmin.setActive(true);
                    userRepo.save(superAdmin);
                    log.info("Initialized initial SUPER_ADMIN account for {}", superAdminEmail.trim());
                } else {
                    log.warn(
                            "Users table is empty and APP_SUPERADMIN_EMAIL / APP_SUPERADMIN_PASSWORD are not configured.");
                }
            }

            if (isProdProfile || (!seedEnabled && !isDevProfile)) {
                return;
            }
            if (orgRepo.count() > 0) {
                return;
            }

            log.info("Seeding full production-like restaurant dataset...");

            OrganizationEntity org = new OrganizationEntity();
            org.setName("مطعم مذاق الأصالة");
            org.setSlug("mazaq-restaurant");
            org.setType(OrganizationType.RESTAURANT_CHAIN);
            org.setPlan(SubscriptionPlan.PRO);
            org.setSubscriptionStatus(SubscriptionStatus.ACTIVE);
            org.setActive(true);
            org.setSubscriptionEndsAt(LocalDateTime.now().plusMonths(3));
            org = orgRepo.save(org);

            BranchEntity mainBranch = saveBranch(branchRepo, org.getId(), "الفرع الرئيسي - المهندسين",
                    "الجيزة", "24 شارع جامعة الدول العربية", "01011223344", 14, 12);
            BranchEntity nasrBranch = saveBranch(branchRepo, org.getId(), "فرع مدينة نصر - عباس العقاد",
                    "القاهرة", "45 شارع عباس العقاد", "01055667788", 14, 10);
            BranchEntity tagamoaBranch = saveBranch(branchRepo, org.getId(), "فرع التجمع الخامس - بوينت 90",
                    "القاهرة الجديدة", "مول بوينت 90 الدور الثاني", "01099887766", 14, 12);

            saveUser(userRepo, encoder, "owner1", "123456", UserRole.ORG_ADMIN, org.getId(), mainBranch.getId(), true,
                    true, "أحمد المدير");
            UserEntity cashier1 = saveUser(userRepo, encoder, "cashier1", "123456", UserRole.CASHIER, org.getId(),
                    mainBranch.getId(), false, true, "سارة الكاشير");
            saveUser(userRepo, encoder, "chef1", "123456", UserRole.KITCHEN_STAFF, org.getId(), mainBranch.getId(),
                    false, true, "الشيف محمود");
            UserEntity waiter1 = saveUser(userRepo, encoder, "waiter1", "123456", UserRole.WAITER, org.getId(),
                    mainBranch.getId(), false, true, "كريم الويتر");
            saveUser(userRepo, encoder, "waiter1_b", "123456", UserRole.WAITER, org.getId(), mainBranch.getId(), false,
                    true, "يوسف الويتر");
            saveUser(userRepo, encoder, "former_staff", "123456", UserRole.WAITER, org.getId(), mainBranch.getId(),
                    false, false, "موظف سابق (موقوف)");

            saveUser(userRepo, encoder, "cashier2", "123456", UserRole.CASHIER, org.getId(), nasrBranch.getId(), false,
                    true, "نور الكاشير");
            saveUser(userRepo, encoder, "chef2", "123456", UserRole.KITCHEN_STAFF, org.getId(), nasrBranch.getId(),
                    false, true, "الشيف طارق");
            saveUser(userRepo, encoder, "waiter2", "123456", UserRole.WAITER, org.getId(), nasrBranch.getId(), false,
                    true, "عمر الويتر");

            saveUser(userRepo, encoder, "cashier3", "123456", UserRole.CASHIER, org.getId(), tagamoaBranch.getId(),
                    false, true, "مريم الكاشير");
            saveUser(userRepo, encoder, "chef3", "123456", UserRole.KITCHEN_STAFF, org.getId(), tagamoaBranch.getId(),
                    false, true, "الشيف حسن");

            for (int i = 1; i <= 10; i++) {
                saveTable(tableRepo, org.getId(), mainBranch.getId(), "T-" + i, "demo-table-" + i, i <= 6 ? 4 : 6,
                        true);
            }
            saveTable(tableRepo, org.getId(), mainBranch.getId(), "VIP-1", "demo-vip-1", 8, true);
            saveTable(tableRepo, org.getId(), mainBranch.getId(), "VIP-2", "demo-vip-2", 10, true);
            saveTable(tableRepo, org.getId(), mainBranch.getId(), "T-11", "demo-table-11", 4, false);

            for (int i = 1; i <= 8; i++) {
                saveTable(tableRepo, org.getId(), nasrBranch.getId(), "N-" + i, "nasr-table-" + i, 4, true);
            }
            for (int i = 1; i <= 6; i++) {
                saveTable(tableRepo, org.getId(), tagamoaBranch.getId(), "C-" + i, "cairo-table-" + i, i <= 4 ? 4 : 6,
                        true);
            }

            List<SeededItem> seededItems = new ArrayList<>();

            MenuCategoryEntity soups = saveCategory(categoryRepo, org.getId(), "الشوربة والسلطات",
                    "شوربات دافئة وسلطات طازجة يومياً", 1);
            seededItems.add(SeededItem.of(saveItem(itemRepo, org.getId(), soups.getId(), "شوربة عدس بالشعرية",
                    "تقدم مع خبز محمص وليمون", 45, 7, true), null));
            seededItems.add(SeededItem.of(saveItem(itemRepo, org.getId(), soups.getId(), "شوربة كريمة دجاج ومشروم",
                    "غنية بالكريمة الطازجة", 65, 10, true), null));
            MenuItemEntity caesar = saveItem(itemRepo, org.getId(), soups.getId(), "سلطة سيزر",
                    "خس روماني مع صوص سيزر وجبنة بارميزان", 85, 8, true);
            ItemVariantEntity caesarChicken = saveVariant(variantRepo, caesar.getId(), "مع دجاج مشوي", 115, true);
            saveVariant(variantRepo, caesar.getId(), "كلاسيك بدون دجاج", 85, false);
            saveAddon(addonRepo, caesar.getId(), "بارميزان إضافية", 20);
            seededItems.add(SeededItem.of(caesar, caesarChicken));
            seededItems.add(SeededItem.of(saveItem(itemRepo, org.getId(), soups.getId(), "سلطة فتوش وطحينة",
                    "خضروات طازجة ودبس رمان", 50, 5, true), null));

            MenuCategoryEntity appetizers = saveCategory(categoryRepo, org.getId(), "المقبلات",
                    "مقبلات شرقية لفتح الشهية", 2);
            MenuItemEntity sambousek = saveItem(itemRepo, org.getId(), appetizers.getId(), "سمبوسك مقرمشة",
                    "لحم مفروم أو ميكس جبن", 65, 10, true);
            ItemVariantEntity samb4 = saveVariant(variantRepo, sambousek.getId(), "4 قطع", 65, true);
            saveVariant(variantRepo, sambousek.getId(), "8 قطع", 115, false);
            seededItems.add(SeededItem.of(sambousek, samb4));

            MenuItemEntity fries = saveItem(itemRepo, org.getId(), appetizers.getId(), "بطاطس مقرمشة بالتوابل",
                    "بطاطس ذهبية مع بهارات الشيف", 50, 8, true);
            ItemVariantEntity friesMed = saveVariant(variantRepo, fries.getId(), "وسط", 50, true);
            saveVariant(variantRepo, fries.getId(), "عائلي", 85, false);
            saveAddon(addonRepo, fries.getId(), "صوص جبنة شيدر", 20);
            saveAddon(addonRepo, fries.getId(), "هالبينو", 10);
            seededItems.add(SeededItem.of(fries, friesMed));

            seededItems.add(SeededItem.of(saveItem(itemRepo, org.getId(), appetizers.getId(), "ورق عنب بدبس الرمان",
                    "6 أصابع ورق عنب مطهوة بالليمون", 75, 8, true), null));
            seededItems.add(SeededItem.of(saveItem(itemRepo, org.getId(), appetizers.getId(), "كبة شامية مقلية",
                    "4 قطع كبة محشوة لحم وصنوبر", 90, 12, true), null));
            seededItems.add(SeededItem.of(saveItem(itemRepo, org.getId(), appetizers.getId(), "حمص باللحمة والمكسرات",
                    "حمص ناعم مع لحم بتلو وصنوبر", 110, 10, true), null));

            MenuCategoryEntity grills = saveCategory(categoryRepo, org.getId(), "المشويات على الفحم",
                    "مشويات بلدي طازجة", 3);
            MenuItemEntity kebab = saveItem(itemRepo, org.getId(), grills.getId(), "كباب مشوي على الفحم",
                    "يقدم مع أرز بسمتي وطحينة وخبز", 220, 20, true);
            saveVariant(variantRepo, kebab.getId(), "ربع كيلو", 130, false);
            ItemVariantEntity kebabHalf = saveVariant(variantRepo, kebab.getId(), "نصف كيلو", 220, true);
            saveVariant(variantRepo, kebab.getId(), "كيلو كامل", 410, false);
            saveAddon(addonRepo, kebab.getId(), "أرز بسمتي إضافي", 35);
            saveAddon(addonRepo, kebab.getId(), "صوص ثومية", 15);
            seededItems.add(SeededItem.of(kebab, kebabHalf));

            MenuItemEntity kofta = saveItem(itemRepo, org.getId(), grills.getId(), "كفتة مشوية على الفحم",
                    "لحم ضاني وبتلو متبل بالبقدونس", 185, 18, true);
            ItemVariantEntity koftaHalf = saveVariant(variantRepo, kofta.getId(), "نصف كيلو", 185, true);
            saveVariant(variantRepo, kofta.getId(), "كيلو كامل", 350, false);
            seededItems.add(SeededItem.of(kofta, koftaHalf));

            MenuItemEntity shish = saveItem(itemRepo, org.getId(), grills.getId(), "شيش طاووق مشوي",
                    "صدور دجاج متبلة ومشوية على الفحم", 165, 18, true);
            ItemVariantEntity shishHalf = saveVariant(variantRepo, shish.getId(), "نصف كيلو", 165, true);
            saveVariant(variantRepo, shish.getId(), "كيلو كامل", 310, false);
            saveAddon(addonRepo, shish.getId(), "ثومية إضافية", 15);
            seededItems.add(SeededItem.of(shish, shishHalf));

            MenuItemEntity mixGrill = saveItem(itemRepo, org.getId(), grills.getId(), "صينية ميكس جريل العائلة",
                    "كباب + كفتة + شيش طاووق + ريش ضاني", 480, 25, true);
            ItemVariantEntity mix2 = saveVariant(variantRepo, mixGrill.getId(), "يكفي فردين", 480, true);
            saveVariant(variantRepo, mixGrill.getId(), "يكفي 4 أفراد", 890, false);
            saveAddon(addonRepo, mixGrill.getId(), "طبق سلطة خضراء كبير", 25);
            seededItems.add(SeededItem.of(mixGrill, mix2));

            saveItem(itemRepo, org.getId(), grills.getId(), "ريش ضاني مشوية (نفذت اليوم)", "تتبيلة أعشاب خاصة", 320, 25,
                    false);

            MenuCategoryEntity mains = saveCategory(categoryRepo, org.getId(), "الطواجن والأطباق الرئيسية",
                    "طواجن في الفرن البلدي", 4);
            seededItems.add(SeededItem.of(saveItem(itemRepo, org.getId(), mains.getId(), "طاجن لحمة بالبصل المكرمل",
                    "لحم موزة مطهو ببطء مع أرز بالشعرية", 230, 22, true), null));
            seededItems.add(SeededItem.of(saveItem(itemRepo, org.getId(), mains.getId(), "فتة موزة ضاني بالخل والثوم",
                    "أرز مصري وخبز محمص وموزة محمرة", 285, 20, true), null));
            seededItems.add(SeededItem.of(saveItem(itemRepo, org.getId(), mains.getId(), "مندي دجاج مدخن",
                    "نصف دجاجة مندي مع أرز بسمتي ودقوس", 175, 18, true), null));
            seededItems.add(SeededItem.of(saveItem(itemRepo, org.getId(), mains.getId(), "مكرونة بشاميل باللحم المفروم",
                    "طاجن فرن ساخن بالجبنة الموتزاريلا", 145, 15, true), null));

            MenuCategoryEntity pizza = saveCategory(categoryRepo, org.getId(), "البيتزا الإيطالية",
                    "عجينة مخبوزة في الفرن الحجري", 5);
            MenuItemEntity margherita = saveItem(itemRepo, org.getId(), pizza.getId(), "بيتزا مارجريتا",
                    "صوص طماطم إيطالي مع موتزاريلا وريحان", 110, 15, true);
            ItemVariantEntity margM = saveVariant(variantRepo, margherita.getId(), "وسط", 110, true);
            saveVariant(variantRepo, margherita.getId(), "كبير", 155, false);
            saveAddon(addonRepo, margherita.getId(), "موتزاريلا إضافية", 25);
            saveAddon(addonRepo, margherita.getId(), "زيتون أسود", 10);
            seededItems.add(SeededItem.of(margherita, margM));

            MenuItemEntity pepperoni = saveItem(itemRepo, org.getId(), pizza.getId(), "بيتزا بيبروني",
                    "شرائح بيبروني بقري مع جبنة موتزاريلا", 145, 15, true);
            ItemVariantEntity pepM = saveVariant(variantRepo, pepperoni.getId(), "وسط", 145, true);
            saveVariant(variantRepo, pepperoni.getId(), "كبير", 195, false);
            saveAddon(addonRepo, pepperoni.getId(), "أطراف محشوة جبنة", 30);
            seededItems.add(SeededItem.of(pepperoni, pepM));

            MenuItemEntity bbqChicken = saveItem(itemRepo, org.getId(), pizza.getId(), "بيتزا دجاج باربيكيو",
                    "صدور دجاج مشوية وبصل أحمر وصوص باربيكيو", 155, 16, true);
            ItemVariantEntity bbqM = saveVariant(variantRepo, bbqChicken.getId(), "وسط", 155, true);
            saveVariant(variantRepo, bbqChicken.getId(), "كبير", 210, false);
            seededItems.add(SeededItem.of(bbqChicken, bbqM));

            MenuCategoryEntity burgers = saveCategory(categoryRepo, org.getId(), "البرجر والساندوتشات",
                    "برجر لحم بلدي طازج", 6);
            MenuItemEntity burger = saveItem(itemRepo, org.getId(), burgers.getId(), "برجر كلاسيك",
                    "لحم بقري مشوي مع خس وطماطم وصوص خاص", 130, 15, true);
            ItemVariantEntity burgerS = saveVariant(variantRepo, burger.getId(), "سنجل", 130, true);
            saveVariant(variantRepo, burger.getId(), "دبل", 180, false);
            saveAddon(addonRepo, burger.getId(), "جبنة شيدر", 20);
            saveAddon(addonRepo, burger.getId(), "بيكون بقري", 30);
            saveAddon(addonRepo, burger.getId(), "هالبينو", 10);
            seededItems.add(SeededItem.of(burger, burgerS));

            MenuItemEntity crispy = saveItem(itemRepo, org.getId(), burgers.getId(), "ساندوتش تشيكن كريسبي",
                    "صدور دجاج مقرمشة مع كول سلو وصوص رانش", 115, 12, true);
            ItemVariantEntity crispyReg = saveVariant(variantRepo, crispy.getId(), "عادي", 115, true);
            saveVariant(variantRepo, crispy.getId(), "سبايسي", 120, false);
            saveAddon(addonRepo, crispy.getId(), "صوص رانش إضافي", 15);
            seededItems.add(SeededItem.of(crispy, crispyReg));

            seededItems.add(SeededItem.of(saveItem(itemRepo, org.getId(), burgers.getId(), "شاورما لحم سوري",
                    "تقدم مع ثومية ومخلل وبطاطس", 125, 12, true), null));

            MenuCategoryEntity drinks = saveCategory(categoryRepo, org.getId(), "المشروبات والقهوة",
                    "عصائر فريش ومشروبات باردة وساخنة", 7);
            seededItems.add(SeededItem.of(saveItem(itemRepo, org.getId(), drinks.getId(), "عصير مانجو فريش",
                    "مانجو طبيعي بدون سكر مضاف", 45, 5, true), null));
            seededItems.add(SeededItem.of(
                    saveItem(itemRepo, org.getId(), drinks.getId(), "ليمون بالنعناع مثلج", "منعش وطازج", 35, 4, true),
                    null));
            seededItems.add(SeededItem.of(saveItem(itemRepo, org.getId(), drinks.getId(), "مشروب غازي (بيبسي / سفن)",
                    "كانز بارد 330 مل", 25, 2, true), null));
            seededItems.add(SeededItem.of(
                    saveItem(itemRepo, org.getId(), drinks.getId(), "مياه معدنية كبيرة", "زجاجة 1 لتر", 18, 1, true),
                    null));
            seededItems.add(SeededItem.of(saveItem(itemRepo, org.getId(), drinks.getId(), "لاتيه مثلج (Iced Latte)",
                    "إسبريسو مع حليب بارد وثلج", 60, 5, true), null));
            saveItem(itemRepo, org.getId(), drinks.getId(), "عصير رمان فريش (موسمي)", "غير متاح خارج الموسم", 50, 5,
                    false);

            MenuCategoryEntity desserts = saveCategory(categoryRepo, org.getId(), "الحلويات",
                    "حلويات شرقية وغربية طازجة", 8);
            MenuItemEntity kunafa = saveItem(itemRepo, org.getId(), desserts.getId(), "كنافة بالمانجو والقشطة",
                    "كنافة محمصة بالزبدة مع كريمة وقطع مانجو", 85, 7, true);
            ItemVariantEntity kunafaSingle = saveVariant(variantRepo, kunafa.getId(), "كوب فردي", 85, true);
            saveVariant(variantRepo, kunafa.getId(), "بولة عائلية", 240, false);
            seededItems.add(SeededItem.of(kunafa, kunafaSingle));

            MenuItemEntity cheesecake = saveItem(itemRepo, org.getId(), desserts.getId(), "تشيز كيك لوتس",
                    "قطعة تشيز كيك غنية بصوص بسكويت اللوتس", 95, 5, true);
            saveAddon(addonRepo, cheesecake.getId(), "صوص لوتس إضافي", 20);
            seededItems.add(SeededItem.of(cheesecake, null));
            seededItems.add(SeededItem.of(saveItem(itemRepo, org.getId(), desserts.getId(), "أم علي بالمكسرات في الفرن",
                    "ساخنة مع قشطة طازجة", 75, 10, true), null));

            seedOrdersAndInvoices(
                    org.getId(), mainBranch, cashier1.getId(), waiter1.getId(),
                    seededItems, orderRepo, tableDomainRepo, invoiceRepo);

            OrganizationEntity org2 = new OrganizationEntity();
            org2.setName("برجر هاوس");
            org2.setSlug("burger-house");
            org2.setType(OrganizationType.SINGLE_BRANCH);
            org2.setPlan(SubscriptionPlan.TRIAL);
            org2.setSubscriptionStatus(SubscriptionStatus.TRIAL);
            org2.setActive(true);
            org2.setTrialEndsAt(LocalDateTime.now().plusDays(14));
            org2 = orgRepo.save(org2);

            BranchEntity bhBranch = saveBranch(branchRepo, org2.getId(), "فرع الزمالك",
                    "القاهرة", "12 شارع البرازيل", "01222334455", 14, 0);
            saveUser(userRepo, encoder, "owner_bh", "123456", UserRole.ORG_ADMIN, org2.getId(), bhBranch.getId(), true,
                    true, "رامي مالك برجر هاوس");
            saveTable(tableRepo, org2.getId(), bhBranch.getId(), "B-1", "bh-table-1", 4, true);
            saveTable(tableRepo, org2.getId(), bhBranch.getId(), "B-2", "bh-table-2", 4, true);

            MenuCategoryEntity bhCat = saveCategory(categoryRepo, org2.getId(), "سماش برجر", "لحم طازج يومياً", 1);
            saveItem(itemRepo, org2.getId(), bhCat.getId(), "دبل سماش برجر", "شريحتان لحم مع شيدر مزدوج", 170, 12,
                    true);
            saveItem(itemRepo, org2.getId(), bhCat.getId(), "ميلك شيك فانيليا", "آيس كريم طبيعي", 65, 4, true);

            log.info("Full restaurant seed completed successfully!");
        };
    }

    private void seedOrdersAndInvoices(Long orgId,
            BranchEntity branch,
            Long cashierId,
            Long waiterId,
            List<SeededItem> items,
            OrderRepositoryPort orderRepo,
            TableRepositoryPort tableDomainRepo,
            InvoiceRepositoryPort invoiceRepo) {
        Long branchId = branch.getId();
        BigDecimal tax = branch.getTaxRate();
        BigDecimal svc = branch.getServiceChargeRate();
        ZoneId zone = ZoneId.of("Africa/Cairo");
        LocalDateTime now = LocalDateTime.now(zone);

        for (int daysAgo = 6; daysAgo >= 1; daysAgo--) {
            LocalDateTime dayTime = now.minusDays(daysAgo).withHour(16).withMinute(0);
            LocalDate dayDate = dayTime.toLocalDate();

            for (int k = 0; k < 4; k++) {
                SeededItem s1 = items.get((daysAgo + k) % items.size());
                SeededItem s2 = items.get((daysAgo * 2 + k + 3) % items.size());

                OrderType type = (k % 2 == 0) ? OrderType.DINE_IN : OrderType.TAKEAWAY;
                OrderSource source = (k == 0) ? OrderSource.QR_CUSTOMER
                        : (k == 1 ? OrderSource.WAITER : OrderSource.CASHIER_POS);

                int orderNo = orderRepo.nextOrderNumber(branchId, dayDate);
                Order o = Order.place(
                        orgId, branchId, null, null, orderNo,
                        UUID.randomUUID().toString().replace("-", ""),
                        type, source, source == OrderSource.CASHIER_POS ? cashierId : waiterId,
                        type == OrderType.TAKEAWAY ? "عميل " + (k + 1) : null, null, null, null,
                        List.of(toOrderItem(s1, 2, null), toOrderItem(s2, 1, null)),
                        tax, svc, dayTime.plusHours(k));
                o.startPreparing(dayTime.plusHours(k).plusMinutes(2));
                o.markReady(dayTime.plusHours(k).plusMinutes(15));
                o.markServed();
                o.complete();
                Order saved = orderRepo.save(o);

                String invNo = invoiceRepo.nextInvoiceNumber(branchId, dayDate);
                PaymentMethod pm = PaymentMethod.values()[k % PaymentMethod.values().length];
                invoiceRepo.save(Invoice.issue(
                        orgId, branchId, null, saved.getId(), invNo,
                        saved.getSubtotal(), saved.getTaxAmount(), saved.getServiceCharge(),
                        BigDecimal.ZERO, pm, cashierId, dayTime.plusHours(k).plusMinutes(30)));
            }
        }

        List<RestaurantTable> branchTables = tableDomainRepo.findByBranchId(branchId);
        if (branchTables.size() < 4)
            return;

        openOccupiedTableOrder(orgId, branchId, branchTables.get(0), OrderStatus.PENDING, OrderSource.QR_CUSTOMER,
                null, List.of(toOrderItem(items.get(5), 1, "تسوية وسط"), toOrderItem(items.get(15), 2, null)),
                "بدون بصل في البرجر", tax, svc, now.minusMinutes(3), orderRepo, tableDomainRepo);

        openOccupiedTableOrder(orgId, branchId, branchTables.get(1), OrderStatus.PREPARING, OrderSource.WAITER,
                waiterId, List.of(toOrderItem(items.get(8), 1, null), toOrderItem(items.get(12), 1, null)),
                null, tax, svc, now.minusMinutes(11), orderRepo, tableDomainRepo);

        openOccupiedTableOrder(orgId, branchId, branchTables.get(2), OrderStatus.READY, OrderSource.QR_CUSTOMER,
                null, List.of(toOrderItem(items.get(10), 2, null), toOrderItem(items.get(18), 2, null)),
                "الصلصة في طبق جانبي", tax, svc, now.minusMinutes(16), orderRepo, tableDomainRepo);

        openOccupiedTableOrder(orgId, branchId, branchTables.get(3), OrderStatus.SERVED, OrderSource.WAITER,
                waiterId, List.of(toOrderItem(items.get(6), 1, null), toOrderItem(items.get(20), 2, null)),
                null, tax, svc, now.minusMinutes(35), orderRepo, tableDomainRepo);

        LocalDate today = now.toLocalDate();
        Order takeawayReady = Order.place(
                orgId, branchId, null, null, orderRepo.nextOrderNumber(branchId, today),
                UUID.randomUUID().toString().replace("-", ""),
                OrderType.TAKEAWAY, OrderSource.CASHIER_POS, cashierId,
                "عميل تيك أواي", "01000001111", null, "تغليف سفري",
                List.of(toOrderItem(items.get(14), 1, null), toOrderItem(items.get(17), 1, null)),
                tax, svc, now.minusMinutes(12));
        takeawayReady.startPreparing(now.minusMinutes(10));
        takeawayReady.markReady(now.minusMinutes(1));
        orderRepo.save(takeawayReady);

        Order deliveryPending = Order.place(
                orgId, branchId, null, null, orderRepo.nextOrderNumber(branchId, today),
                UUID.randomUUID().toString().replace("-", ""),
                OrderType.DELIVERY, OrderSource.CASHIER_POS, cashierId,
                "حسام علي", "01122334455", "18 شارع البطل أحمد عبدالعزيز", "الاتصال عند الوصول",
                List.of(toOrderItem(items.get(7), 2, null)),
                tax, svc, now.minusMinutes(2));
        orderRepo.save(deliveryPending);
    }

    private void openOccupiedTableOrder(Long orgId, Long branchId, RestaurantTable table,
            OrderStatus targetStatus, OrderSource source, Long actorId,
            List<OrderItem> orderItems, String notes,
            BigDecimal tax, BigDecimal svc, LocalDateTime createdAt,
            OrderRepositoryPort orderRepo, TableRepositoryPort tableDomainRepo) {
        table.occupy();
        tableDomainRepo.save(table);
        TableSession session = tableDomainRepo.saveSession(
                TableSession.openNew(orgId, branchId, table.getId(), UUID.randomUUID().toString(), createdAt));
        Order order = Order.place(
                orgId, branchId, table.getId(), session.getId(),
                orderRepo.nextOrderNumber(branchId, createdAt.toLocalDate()),
                UUID.randomUUID().toString().replace("-", ""),
                OrderType.DINE_IN, source, actorId,
                null, null, null, notes, orderItems, tax, svc, createdAt);
        if (targetStatus == OrderStatus.PREPARING || targetStatus == OrderStatus.READY
                || targetStatus == OrderStatus.SERVED) {
            order.startPreparing(createdAt.plusMinutes(2));
        }
        if (targetStatus == OrderStatus.READY || targetStatus == OrderStatus.SERVED) {
            order.markReady(createdAt.plusMinutes(12));
        }
        if (targetStatus == OrderStatus.SERVED) {
            order.markServed();
        }
        orderRepo.save(order);
    }

    private OrderItem toOrderItem(SeededItem si, int qty, String notes) {
        BigDecimal price = si.variant != null ? si.variant.getPrice() : si.item.getBasePrice();
        return new OrderItem(
                null, si.item.getId(), si.item.getName(),
                si.variant != null ? si.variant.getId() : null,
                si.variant != null ? si.variant.getName() : null,
                qty, price, notes, List.of());
    }

    private record SeededItem(MenuItemEntity item, ItemVariantEntity variant) {
        static SeededItem of(MenuItemEntity i, ItemVariantEntity v) {
            return new SeededItem(i, v);
        }
    }

    private BranchEntity saveBranch(BranchJpaRepository repo, Long orgId, String name, String city,
            String address, String phone, double tax, double svc) {
        BranchEntity b = new BranchEntity();
        b.setOrganizationId(orgId);
        b.setName(name);
        b.setCity(city);
        b.setAddress(address);
        b.setPhone(phone);
        b.setTaxRate(BigDecimal.valueOf(tax));
        b.setServiceChargeRate(BigDecimal.valueOf(svc));
        return repo.save(b);
    }

    private UserEntity saveUser(UserJpaRepository repo, PasswordEncoder encoder, String username, String rawPass,
            UserRole role, Long orgId, Long branchId, boolean owner, boolean active, String fullName) {
        UserEntity u = new UserEntity();
        u.setUsername(username);
        u.setPassword(encoder.encode(rawPass));
        u.setRole(role);
        u.setOrganizationId(orgId);
        u.setBranchId(branchId);
        u.setOwner(owner);
        u.setActive(active);
        u.setFullName(fullName);
        return repo.save(u);
    }

    private void saveTable(RestaurantTableJpaRepository repo, Long orgId, Long branchId,
            String number, String qrToken, int capacity, boolean active) {
        RestaurantTableEntity t = new RestaurantTableEntity();
        t.setOrganizationId(orgId);
        t.setBranchId(branchId);
        t.setTableNumber(number);
        t.setQrToken(qrToken);
        t.setCapacity(capacity);
        t.setStatus(TableStatus.AVAILABLE);
        t.setActive(active);
        repo.save(t);
    }

    private MenuCategoryEntity saveCategory(MenuCategoryJpaRepository repo, Long orgId,
            String name, String desc, int order) {
        MenuCategoryEntity c = new MenuCategoryEntity();
        c.setOrganizationId(orgId);
        c.setName(name);
        c.setDescription(desc);
        c.setSortOrder(order);
        c.setActive(true);
        return repo.save(c);
    }

    private MenuItemEntity saveItem(MenuItemJpaRepository repo, Long orgId, Long catId,
            String name, String desc, double price, int prepMins, boolean available) {
        MenuItemEntity i = new MenuItemEntity();
        i.setOrganizationId(orgId);
        i.setCategoryId(catId);
        i.setName(name);
        i.setDescription(desc);
        i.setBasePrice(BigDecimal.valueOf(price));
        i.setPrepTimeMinutes(prepMins);
        i.setAvailable(available);
        i.setActive(true);
        return repo.save(i);
    }

    private ItemVariantEntity saveVariant(ItemVariantJpaRepository repo, Long itemId, String name, double price,
            boolean def) {
        ItemVariantEntity v = new ItemVariantEntity();
        v.setMenuItemId(itemId);
        v.setName(name);
        v.setPrice(BigDecimal.valueOf(price));
        v.setDefault(def);
        v.setActive(true);
        return repo.save(v);
    }

    private void saveAddon(ItemAddonJpaRepository repo, Long itemId, String name, double price) {
        ItemAddonEntity a = new ItemAddonEntity();
        a.setMenuItemId(itemId);
        a.setName(name);
        a.setPrice(BigDecimal.valueOf(price));
        a.setAvailable(true);
        repo.save(a);
    }
}