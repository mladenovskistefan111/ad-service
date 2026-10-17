# API

Package `hipstershop`, service `AdService` ([`src/main/proto/ad.proto`](https://github.com/platform-boutique/app-ad-service/blob/main/src/main/proto/ad.proto)).

| Method | Request | Response | Behaviour |
|---|---|---|---|
| `GetAds` | `AdRequest{ repeated string context_keys }` | `AdResponse{ repeated Ad ads }` (`Ad{ redirect_url, text }`) | For every context key, returns **all** ads registered for that category. If the result is empty (no keys, or no category matched), returns 2 random ads (may contain the same ad twice). |

Built-in categories: `clothing`, `accessories`, `footwear`, `hair`, `decor`, `kitchen` (two ads). Redirect URLs are relative (`/product/<id>`) and point at products in `product-catalog-db`.
