package com.saffron.storefront.config;

import com.saffron.storefront.domain.Product;
import com.saffron.storefront.domain.ProductCategory;
import com.saffron.storefront.repository.ProductRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Inserts a minimal Azerbaijani-restaurant menu the first time the schema is empty.
 * Idempotent — won't touch existing rows.
 */
@Configuration
public class SeedData implements CommandLineRunner {

    private final ProductRepository productRepository;

    public SeedData(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (productRepository.count() > 0) return;

        seed("plov-yagnieci", ProductCategory.MAIN,
                "Płow z jagnięciną", "Lamb plov", "Quzu plovu",
                "Tradycyjny azerbejdżański ryż z jagnięciną, suszonymi owocami i szafranem.",
                "Traditional Azerbaijani rice with lamb, dried fruit, and saffron.",
                "Ənənəvi quzu plovu — qurudulmuş meyvələr və zəfəranla.",
                new BigDecimal("58.00"), 650, 25, true, false, 10);

        seed("dolma-lisciem-winogron", ProductCategory.STARTER,
                "Dolma w liściach winogron", "Vine-leaf dolma", "Yarpaq dolması",
                "Liście winogron faszerowane mięsem i ryżem, podawane z jogurtem.",
                "Grape leaves stuffed with meat and rice, served with yogurt.",
                "Üzüm yarpağına bükülmüş ət və düyü dolması, qatıqla.",
                new BigDecimal("38.00"), 400, 20, true, false, 20);

        seed("kebab-lula", ProductCategory.GRILL,
                "Lula kebab", "Lyulya kebab", "Lülə kabab",
                "Soczysty kebab z mielonej jagnięciny, grillowany na szpadkach.",
                "Juicy minced-lamb kebab, char-grilled on skewers.",
                "Qiymə quzu ətindən hazırlanmış sulu lülə kabab.",
                new BigDecimal("52.00"), 350, 18, true, false, 30);

        seed("pakhlava-bakijska", ProductCategory.DESSERT,
                "Pakhlava bakińska", "Baku pakhlava", "Bakı paxlavası",
                "Warstwowe ciasto z orzechami, miodem i przyprawami.",
                "Layered pastry with nuts, honey, and spices.",
                "Qoz, bal və ədviyyatlarla qatlanmış paxlava.",
                new BigDecimal("18.00"), 120, 5, true, true, 40);

        seed("herbata-azerska", ProductCategory.DRINK,
                "Herbata azerska z czaberkiem", "Azerbaijani tea with savory", "Azərbaycan çayı",
                "Aromatyczna czarna herbata podawana w tradycyjnym armudu.",
                "Aromatic black tea served in a traditional armudu glass.",
                "Armudu stəkanda təqdim olunan ətirli qara çay.",
                new BigDecimal("12.00"), 250, 5, true, false, 50);

        seed("dzem-z-pigwy", ProductCategory.JAR,
                "Dżem z pigwy", "Quince preserve", "Heyva mürəbbəsi",
                "Domowy dżem azerbejdżański w słoiku 350 g — pasuje do herbaty i deserów.",
                "Homemade Azerbaijani quince preserve in a 350 g jar — perfect with tea.",
                "Evdə hazırlanmış 350 q heyva mürəbbəsi — çay və desertlərə uyğundur.",
                new BigDecimal("36.00"), 450, 0, false, true, 10);

        seed("przyprawa-szafran", ProductCategory.MERCH,
                "Szafran irański (1 g)", "Iranian saffron (1 g)", "İran zəfəranı (1 q)",
                "Wysokiej jakości szafran, używany w naszej kuchni.",
                "Premium saffron used in our kitchen.",
                "Yüksək keyfiyyətli zəfəran — restoranımızda istifadə etdiyimiz.",
                new BigDecimal("48.00"), 30, 0, false, true, 20);
    }

    private void seed(
            String slug,
            ProductCategory category,
            String namePl, String nameEn, String nameAz,
            String descPl, String descEn, String descAz,
            BigDecimal price,
            int weightGrams,
            int prepMinutes,
            boolean instant,
            boolean courier,
            int sortOrder) {
        if (productRepository.findBySlug(slug).isPresent()) return;
        Product p = new Product();
        p.setSlug(slug);
        p.setCategory(category);
        p.setNamePl(namePl);
        p.setNameEn(nameEn);
        p.setNameAz(nameAz);
        p.setDescriptionPl(descPl);
        p.setDescriptionEn(descEn);
        p.setDescriptionAz(descAz);
        p.setPrice(price);
        p.setWeightGrams(weightGrams);
        p.setPrepMinutes(prepMinutes);
        p.setAvailableInstant(instant);
        p.setAvailableCourier(courier);
        p.setActive(true);
        p.setSortOrder(sortOrder);
        productRepository.save(p);
    }
}
