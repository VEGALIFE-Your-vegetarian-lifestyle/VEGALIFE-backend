# API Error Responses

Every endpoint returns the same envelope on failure:

```json
{
  "success": false,
  "message": "…",
  "data": null
}
```

`GET /api/posts/{postId}` and other endpoints documented under `docs/apis/` all share this shape.

## Field-level validation errors (`400`)

When a request body or query/path parameter fails bean validation, the response is `400` with the top-level
`message` set to `"Validation failed"` and **`data` carrying a field → message map** naming each offending
field:

```json
{
  "success": false,
  "message": "Validation failed",
  "data": {
    "title": "Title is required",
    "featuredImageUrl": "must be a valid URL"
  }
}
```

- Keys are the invalid field names; values are the constraint messages. When the same field has more than one
  violation, the last one wins (the map holds one message per field).
- Query and path parameter violations (e.g. `size` out of range, a non-UUID path variable) are reported the
  same way under `data`, keyed by the parameter/field name.
- A malformed or unreadable request body returns `400` with `"Malformed request body"` and `data: null` — it is
  not a field-level error.

Business-rule rejections that are not bean validation (e.g. publishing without a category) return `400` with
the specific rule message in `message` and `data: null`; see each endpoint's reference and `docs/brs/`.

## Other status codes

| Status | When | `message` |
|--------|------|-----------|
| `400` | Bean-validation failure | `Validation failed`, field map in `data` |
| `400` | Malformed request body | `Malformed request body` |
| `400` | Invalid parameter type (e.g. non-UUID path) | `Invalid value for parameter '<name>'` |
| `400` | Missing required parameter | `Missing required parameter '<name>'` |
| `400` | Business-rule rejection | the specific rule message |
| `401` | Missing/invalid/expired token, inactive account | `Unauthorized` |
| `403` | Authenticated but not permitted | `Forbidden` |
| `404` | Resource not found / soft-deleted / not visible | the specific message (e.g. `Post not found`) |
| `405` | HTTP method not supported on the path | `Method <verb> is not supported` |
| `409` | Duplicate resource / data-integrity conflict | the specific message |
| `413` | Upload exceeds the maximum size | `Uploaded file exceeds the maximum allowed size` |
| `429` | AI quota exceeded | the specific message (with `Retry-After`) |
| `502` | Upstream AI/filter provider failure | the specific message |
| `503` | Payment gateway not configured/unavailable | the specific message |
| `500` | Unexpected server error | `Internal server error` (trace logged server-side) |

## Related

- Endpoint references: `docs/apis/index.md`
- Business rules that produce `400` business-rule messages: `docs/brs/`
