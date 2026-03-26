package hipstershop;

import com.google.common.collect.ImmutableListMultimap;
import java.util.Collection;
import java.util.List;
import java.util.Random;

/**
 * Static catalog of available ads, keyed by product category.
 * Extracted from AdService to keep data and server logic separate.
 */
final class AdsMap {

    private static final int MAX_ADS_TO_SERVE = 2;
    private static final Random RANDOM = new Random();

    private static final ImmutableListMultimap<String, Ad> ADS =
        ImmutableListMultimap.<String, Ad>builder()
            .putAll("clothing",    ad("/product/66VCHSJNUP", "Tank top for sale. 20% off."))
            .putAll("accessories", ad("/product/1YMWWN1N4O", "Watch for sale. Buy one, get second kit for free"))
            .putAll("footwear",    ad("/product/L9ECAV7KIM", "Loafers for sale. Buy one, get second one for free"))
            .putAll("hair",        ad("/product/2ZYFJ3GM2N", "Hairdryer for sale. 50% off."))
            .putAll("decor",       ad("/product/0PUK6V6EV0", "Candle holder for sale. 30% off."))
            .putAll("kitchen",     ad("/product/9SIQT8TOJO", "Bamboo glass jar for sale. 10% off."))
            .putAll("kitchen",     ad("/product/6E92ZMYYFZ", "Mug for sale. Buy two, get third one for free"))
            .build();

    private AdsMap() {}

    static Collection<Ad> getByCategory(String category) {
        return ADS.get(category);
    }

    static List<Ad> getRandom() {
        List<Ad> result = new java.util.ArrayList<>(MAX_ADS_TO_SERVE);
        Collection<Ad> all = ADS.values();
        for (int i = 0; i < MAX_ADS_TO_SERVE; i++) {
            result.add(com.google.common.collect.Iterables.get(all, RANDOM.nextInt(all.size())));
        }
        return result;
    }

    private static Ad ad(String redirectUrl, String text) {
        return Ad.newBuilder()
            .setRedirectUrl(redirectUrl)
            .setText(text)
            .build();
    }
}