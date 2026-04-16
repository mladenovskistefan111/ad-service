package hipstershop;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Collection;
import java.util.List;
import org.junit.jupiter.api.Test;

class AdsMapTest {

    @Test
    void getByCategory_knownCategory_returnsAds() {
        Collection<Ad> ads = AdsMap.getByCategory("clothing");
        assertFalse(ads.isEmpty(), "clothing category should return at least one ad");
    }

    @Test
    void getByCategory_unknownCategory_returnsEmpty() {
        Collection<Ad> ads = AdsMap.getByCategory("nonexistent-category");
        assertTrue(ads.isEmpty(), "unknown category should return empty collection");
    }

    @Test
    void getByCategory_kitchenCategory_returnsTwoAds() {
        Collection<Ad> ads = AdsMap.getByCategory("kitchen");
        assertEquals(2, ads.size(), "kitchen should have exactly 2 ads");
    }

    @Test
    void getByCategory_adHasNonBlankFields() {
        Collection<Ad> ads = AdsMap.getByCategory("accessories");
        Ad ad = ads.iterator().next();
        assertFalse(ad.getRedirectUrl().isBlank(), "redirect URL should not be blank");
        assertFalse(ad.getText().isBlank(), "ad text should not be blank");
    }

    @Test
    void getRandom_returnsExactlyTwoAds() {
        List<Ad> ads = AdsMap.getRandom();
        assertEquals(2, ads.size(), "getRandom should always return exactly 2 ads");
    }

    @Test
    void getRandom_adsHaveNonBlankFields() {
        List<Ad> ads = AdsMap.getRandom();
        for (Ad ad : ads) {
            assertFalse(ad.getRedirectUrl().isBlank());
            assertFalse(ad.getText().isBlank());
        }
    }
}
