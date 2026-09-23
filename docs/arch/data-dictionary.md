# Data Dictionary

## Table 1: User

_Description: Stores user account credentials, status, and role-based access control (Admin, User)._

| Field Name | Data Type | Key | Allow Null | Description |
|------------|-----------|-----|------------|-------------|
| id | UUID | PK | No | Unique identifier for each user |
| username | VARCHAR(50) | Unique | No | Unique login username |
| email | VARCHAR(100) | Unique | No | Registered email address |
| password_hash | VARCHAR(255) | - | No | BCrypt hashed password |
| role | VARCHAR(20) | - | No | User role: ADMIN, USER |
| status | VARCHAR(20) | - | No | Account status: created, activated, deactivated, suspended |
| email_verified | BOOLEAN | - | No | Email verification status |
| last_login_at | TIMESTAMPTZ | - | Yes | Timestamp of last successful login |
| avatar_url | TEXT | - | Yes | Profile picture URL |
| created_at | TIMESTAMPTZ | - | No | Timestamp of account registration |
| updated_at | TIMESTAMPTZ | - | No | Timestamp of last update |
| deleted_at | TIMESTAMPTZ | - | Yes | Soft delete timestamp |

**Constraints:**
- `chk_user_role`: role IN ('ADMIN','USER')
- `chk_user_status`: status IN ('created','activated','deactivated','suspended')

**Indexes:**
- `idx_user_email` ON (email)
- `idx_user_status` ON (status) WHERE deleted_at IS NULL

---

## Table 2: UserProfile

_Description: Stores physical body metrics used for meal planning._

| Field Name | Data Type | Key | Allow Null | Description |
|------------|-----------|-----|------------|-------------|
| id | UUID | PK | No | Unique identifier for user profile |
| user_id | UUID | FK, Unique | No | References User(id) (1-to-1 relationship) |
| height_cm | DECIMAL(5,2) | - | No | User height in centimeters |
| weight_kg | DECIMAL(5,2) | - | No | User weight in kilograms |
| age | INT | - | No | User age |
| gender | VARCHAR(20) | - | No | User sex: male, female, other |
| description | TEXT | - | Yes | User description about themself |
| updated_at | TIMESTAMPTZ | - | No | Timestamp of the last metric update |

**Constraints:**
- `chk_gender`: gender IN ('male','female','other')
- FK: user_id REFERENCES "user"(id) ON DELETE CASCADE

**Indexes:**
- `idx_user_profile_user_id` ON (user_id)

---

## Table 3: Category

_Description: Classification for recipes, blog posts, and cooking videos._

| Field Name | Data Type | Key | Allow Null | Description |
|------------|-----------|-----|------------|-------------|
| id | UUID | PK | No | Unique identifier for category |
| name | VARCHAR(100) | - | No | Category name (e.g., Pure Vegan, High-Protein Vegan) |
| description | TEXT | - | Yes | Detailed description of the category |
| created_at | TIMESTAMPTZ | - | No | Timestamp when category is created |
| updated_at | TIMESTAMPTZ | - | No | Timestamp of last update |
| deleted_at | TIMESTAMPTZ | - | Yes | Soft delete timestamp |

**Indexes:**
- `idx_category_active` ON (id) WHERE deleted_at IS NULL

---

## Table 4: Post

_Description: Manages community blog posts and vegetarian articles._

| Field Name | Data Type | Key | Allow Null | Description |
|------------|-----------|-----|------------|-------------|
| id | UUID | PK | No | Unique identifier for each post |
| user_id | UUID | FK | No | References User(id) - Author of the post |
| location_id | UUID | FK | Yes | References Location(id) - Location tagged in post |
| title | VARCHAR(255) | - | No | Title of the blog post |
| content | TEXT | - | No | Full content body of the post |
| featured_image_url | TEXT | - | Yes | Thumbnail image for feed display |
| status | VARCHAR(20) | - | No | Post status: created, processed, published, unpublished, hidden |
| view_count | INT | - | No | Number of views |
| published_at | TIMESTAMPTZ | - | Yes | Actual publish timestamp |
| created_at | TIMESTAMPTZ | - | No | Timestamp when the post was created |
| updated_at | TIMESTAMPTZ | - | No | Timestamp when the post was updated |
| deleted_at | TIMESTAMPTZ | - | Yes | Soft delete timestamp |

**Constraints:**
- `chk_post_status`: status IN ('created','processed','published','unpublished','hidden')
- FK: user_id REFERENCES "user"(id) ON DELETE CASCADE
- FK: location_id REFERENCES location(id) ON DELETE SET NULL

**Indexes:**
- `idx_post_user_id` ON (user_id)
- `idx_post_location_id` ON (location_id)
- `idx_post_status` ON (status) WHERE deleted_at IS NULL
- `idx_post_published_at` ON (published_at)

---

## Table 5: Post_Category

_Description: Junction table connecting posts with categories (M:N relationship)._

| Field Name | Data Type | Key | Allow Null | Description |
|------------|-----------|-----|------------|-------------|
| post_id | UUID | PK, FK | No | References Post(id) |
| category_id | UUID | PK, FK | No | References Category(id) |

**Constraints:**
- FK: post_id REFERENCES post(id) ON DELETE CASCADE
- FK: category_id REFERENCES category(id) ON DELETE CASCADE

---

## Table 6: Post_Media

_Description: Junction table connecting posts with reference media (M:N relationship)._

| Field Name | Data Type | Key | Allow Null | Description |
|------------|-----------|-----|------------|-------------|
| post_id | UUID | PK, FK | No | References Post(id) |
| media_id | UUID | PK, FK | No | References Media(id) |

**Constraints:**
- FK: post_id REFERENCES post(id) ON DELETE CASCADE
- FK: media_id REFERENCES media(id) ON DELETE CASCADE

---

## Table 7: Post_Recipe

_Description: Junction table connecting posts with reference recipes (M:N relationship)._

| Field Name | Data Type | Key | Allow Null | Description |
|------------|-----------|-----|------------|-------------|
| post_id | UUID | PK, FK | No | References Post(id) |
| recipe_id | UUID | PK, FK | No | References Recipe(id) |

**Constraints:**
- FK: post_id REFERENCES post(id) ON DELETE CASCADE
- FK: recipe_id REFERENCES recipe(id) ON DELETE CASCADE

---

## Table 8: Media

_Description: Manages user-uploaded vegan cooking tutorial videos._

| Field Name | Data Type | Key | Allow Null | Description |
|------------|-----------|-----|------------|-------------|
| id | UUID | PK | No | Unique identifier for each video |
| media_url | TEXT | - | No | File storage URL |
| thumbnail_url | TEXT | - | Yes | Video preview image URL |
| description | TEXT | - | Yes | Summary or cooking instructions |
| status | VARCHAR(20) | - | No | Media upload status: uploading, succeed, failed |
| duration_seconds | INT | - | Yes | Video duration in seconds |
| file_size_bytes | BIGINT | - | Yes | File size in bytes |
| mime_type | VARCHAR(100) | - | Yes | MIME type (e.g., video/mp4) |
| width | INT | - | Yes | Video width in pixels |
| height | INT | - | Yes | Video height in pixels |
| created_at | TIMESTAMPTZ | - | No | Timestamp of upload |
| updated_at | TIMESTAMPTZ | - | No | Timestamp of last update |
| deleted_at | TIMESTAMPTZ | - | Yes | Soft delete timestamp |

**Constraints:**
- `chk_media_status`: status IN ('uploading','succeed','failed')

**Indexes:**
- `idx_media_status` ON (status) WHERE deleted_at IS NULL

---

## Table 9: Comment

_Description: Manages user comments on blog posts._

| Field Name | Data Type | Key | Allow Null | Description |
|------------|-----------|-----|------------|-------------|
| id | UUID | PK | No | Unique identifier for each comment |
| user_id | UUID | FK | No | References User(id) - Commenter |
| parent_id | UUID | FK | Yes | References Comment(id) - Nested comment |
| post_id | UUID | FK | No | References Post(id) |
| content | TEXT | - | No | Comment text |
| created_at | TIMESTAMPTZ | - | No | Timestamp when the comment was submitted |
| updated_at | TIMESTAMPTZ | - | No | Timestamp of last update |
| deleted_at | TIMESTAMPTZ | - | Yes | Soft delete timestamp |

**Constraints:**
- FK: user_id REFERENCES "user"(id) ON DELETE CASCADE
- FK: parent_id REFERENCES comment(id) ON DELETE CASCADE
- FK: post_id REFERENCES post(id) ON DELETE CASCADE

**Indexes:**
- `idx_comment_post_id` ON (post_id)
- `idx_comment_user_id` ON (user_id)
- `idx_comment_parent_id` ON (parent_id)
- `idx_comment_active` ON (id) WHERE deleted_at IS NULL

---

## Table 10: Vote

_Description: Tracks user votes/likes for posts._

| Field Name | Data Type | Key | Allow Null | Description |
|------------|-----------|-----|------------|-------------|
| id | UUID | PK | No | Unique identifier for each vote |
| user_id | UUID | FK | No | References User(id) |
| post_id | UUID | FK | No | References Post(id) |
| vote_type | VARCHAR(20) | - | No | Interaction type: UPVOTE, DOWNVOTE |
| created_at | TIMESTAMPTZ | - | No | Timestamp when vote was cast |

**Constraints:**
- `chk_vote_type`: vote_type IN ('UPVOTE','DOWNVOTE')
- FK: user_id REFERENCES "user"(id) ON DELETE CASCADE
- FK: post_id REFERENCES post(id) ON DELETE CASCADE
- `uq_vote_user_post`: UNIQUE (user_id, post_id)

**Indexes:**
- `idx_vote_post_id` ON (post_id)

---

## Table 11: Ingredient

_Description: Master list of vegan food materials/ingredients available in the system._

| Field Name | Data Type | Key | Allow Null | Description |
|------------|-----------|-----|------------|-------------|
| id | UUID | PK | No | Unique identifier for ingredient |
| name | VARCHAR(100) | - | No | Ingredient name (e.g., Tofu, Mushroom, Spinach) |
| calories | DECIMAL(10,2) | - | Yes | Calories per 100g |
| protein_g | DECIMAL(10,2) | - | Yes | Protein per 100g |
| carbohydrate_g | DECIMAL(10,2) | - | Yes | Carbohydrates per 100g |
| fat_g | DECIMAL(10,2) | - | Yes | Fat per 100g |
| fiber_g | DECIMAL(10,2) | - | Yes | Fiber per 100g |
| created_at | TIMESTAMPTZ | - | No | Timestamp of creation |
| updated_at | TIMESTAMPTZ | - | No | Timestamp of last update |

---

## Table 12: Recipe

_Description: Manages vegan recipes, preparation steps, and calorie values._

| Field Name | Data Type | Key | Allow Null | Description |
|------------|-----------|-----|------------|-------------|
| id | UUID | PK | No | Unique identifier for each recipe |
| dish_id | UUID | FK | No | References Dish(id) - Canonical dish this recipe variation belongs to |
| user_id | UUID | FK | No | Reference to user_id to know who posted the recipe |
| name | VARCHAR(255) | - | No | Recipe name |
| description | TEXT | - | Yes | Recipe description |
| instructions | TEXT | - | No | Detailed step-by-step cooking instructions |
| prep_time_minutes | INT | - | Yes | Estimated preparation duration in minutes |
| cook_time_minutes | INT | - | Yes | Estimated cooking duration in minutes |
| servings | INT | - | No | Number of servings |
| difficulty | VARCHAR(20) | - | Yes | Difficulty: EASY, MEDIUM, HARD |
| created_at | TIMESTAMPTZ | - | No | Timestamp when the recipe is created |
| updated_at | TIMESTAMPTZ | - | No | Timestamp when the recipe is updated |
| deleted_at | TIMESTAMPTZ | - | Yes | Soft delete timestamp |

**Constraints:**
- `chk_difficulty`: difficulty IN ('EASY','MEDIUM','HARD')
- FK: dish_id REFERENCES dish(id) ON DELETE CASCADE
- FK: user_id REFERENCES "user"(id) ON DELETE CASCADE

**Indexes:**
- `idx_recipe_user_id` ON (user_id)
- `idx_recipe_dish_id` ON (dish_id)
- `idx_recipe_active` ON (id) WHERE deleted_at IS NULL

---

## Table 13: Recipe_Ingredient

_Description: Junction table connecting recipes with required ingredients (M:N relationship)._

| Field Name | Data Type | Key | Allow Null | Description |
|------------|-----------|-----|------------|-------------|
| recipe_id | UUID | PK, FK | No | References Recipe(id) |
| ingredient_id | UUID | PK, FK | No | References Ingredient(id) |
| amount | DECIMAL(10,3) | - | No | Required quantity for this recipe |
| unit | VARCHAR(30) | - | No | Measurement unit (e.g., g, ml, tsp, tbsp, cup) |

**Constraints:**
- FK: recipe_id REFERENCES recipe(id) ON DELETE CASCADE
- FK: ingredient_id REFERENCES ingredient(id) ON DELETE CASCADE

---

## Table 14: Dish

_Description: Canonical vegan dishes — each dish can have multiple recipe variations._

| Field Name | Data Type | Key | Allow Null | Description |
|------------|-----------|-----|------------|-------------|
| id | UUID | PK | No | Unique identifier for each dish |
| name | TEXT | - | No | Dish name |
| description | TEXT | - | Yes | Dish description |
| image_url | TEXT | - | Yes | Representative image of the dish |
| cuisine_type | VARCHAR(50) | - | Yes | Cuisine classification (e.g., Vietnamese, Mediterranean) |
| created_at | TIMESTAMPTZ | - | No | Timestamp when dish is created |
| updated_at | TIMESTAMPTZ | - | No | Timestamp of last update |
| deleted_at | TIMESTAMPTZ | - | Yes | Soft delete timestamp |

**Indexes:**
- `idx_dish_active` ON (id) WHERE deleted_at IS NULL

---

## Table 15: Menu

_Description: User-specific weekly meal plan generated based on BMI and available ingredients._

| Field Name | Data Type | Key | Allow Null | Description |
|------------|-----------|-----|------------|-------------|
| id | UUID | PK | No | Unique identifier for the weekly menu |
| user_id | UUID | FK | No | References User(id) |
| start_date | DATE | - | No | Start date of the meal planning |
| end_date | DATE | - | No | End date of the meal planning |
| status | VARCHAR(20) | - | No | Status: drafted, scheduled, cancelled, completed |
| notes | TEXT | - | Yes | AI summary: calories, protein, goal (weight loss/muscle gain), etc. |
| created_at | TIMESTAMPTZ | - | No | Timestamp of menu first generation |
| updated_at | TIMESTAMPTZ | - | No | Timestamp of menu last modification |

**Constraints:**
- `chk_menu_status`: status IN ('drafted','scheduled','cancelled','completed')
- FK: user_id REFERENCES "user"(id) ON DELETE CASCADE

**Indexes:**
- `idx_menu_user_id` ON (user_id)
- `idx_menu_start_date` ON (start_date)

---

## Table 16: MenuDetail

_Description: Maps specific dishes to individual days and meals within a weekly plan._

| Field Name | Data Type | Key | Allow Null | Description |
|------------|-----------|-----|------------|-------------|
| id | UUID | PK | No | Unique identifier for menu line item |
| menu_id | UUID | FK | No | References Menu(id) |
| dish_id | UUID | FK | No | References Dish(id) |
| date | DATE | - | No | Date in scheduled menu |
| meal_type | VARCHAR(20) | - | No | Meal type: BREAKFAST, LUNCH, DINNER |
| created_at | TIMESTAMPTZ | - | No | Timestamp of creation |

**Constraints:**
- `chk_meal_type`: meal_type IN ('BREAKFAST','LUNCH','DINNER')
- FK: menu_id REFERENCES menu(id) ON DELETE CASCADE
- FK: dish_id REFERENCES dish(id) ON DELETE CASCADE
- `uq_menu_detail_day_meal`: UNIQUE (menu_id, date, meal_type)

**Indexes:**
- `idx_menu_detail_menu_id` ON (menu_id)
- `idx_menu_detail_dish_id` ON (dish_id)
- `idx_menu_detail_date_meal` ON (date, meal_type)

---

## Table 17: Location

_Description: Stores vegan restaurant and grocery store locations for map discovery._

| Field Name | Data Type | Key | Allow Null | Description |
|------------|-----------|-----|------------|-------------|
| id | UUID | PK | No | Unique identifier for each location |
| name | VARCHAR(255) | - | No | Store / Restaurant name |
| address | VARCHAR(255) | - | No | Physical street address |
| latitude | DECIMAL(9,6) | - | No | GPS latitude coordinate |
| longitude | DECIMAL(9,6) | - | No | GPS longitude coordinate |
| phone_number | VARCHAR(20) | - | Yes | Contact phone number |
| place_type | VARCHAR(20) | - | No | Type: restaurant, grocery, cafe, market |
| opening_hours | JSONB | - | Yes | Structured opening hours for "open now" queries |
| website_url | TEXT | - | Yes | External website URL |
| rating | DECIMAL(2,1) | - | Yes | Average rating (0.0-5.0) |
| price_level | INT | - | Yes | Price level: 1 ($) to 4 ($$$$) |
| created_at | TIMESTAMPTZ | - | No | Timestamp of creation |
| updated_at | TIMESTAMPTZ | - | No | Timestamp of last update |
| deleted_at | TIMESTAMPTZ | - | Yes | Soft delete timestamp |

**Constraints:**
- `chk_place_type`: place_type IN ('restaurant','grocery','cafe','market')
- `chk_price_level`: price_level BETWEEN 1 AND 4

**Indexes:**
- `idx_location_active` ON (id) WHERE deleted_at IS NULL
- `idx_location_place_type` ON (place_type)
- `idx_location_coords` ON (latitude, longitude)
- `idx_location_opening_hours` ON (opening_hours) USING GIN

---

## Table 18: AIConversation

_Description: Tracks AI Chatbot conversations._

| Field Name | Data Type | Key | Allow Null | Description |
|------------|-----------|-----|------------|-------------|
| id | UUID | PK | No | Unique identifier for chat session |
| user_id | UUID | FK | Yes | References User(id) (Null for guest users) |
| title | TEXT | - | No | Title of conversation |
| summary | TEXT | - | Yes | Summary of conversation |
| recent_messages | JSONB | - | Yes | Recent messages for context |
| created_at | TIMESTAMPTZ | - | No | Conversation start timestamp |
| updated_at | TIMESTAMPTZ | - | No | Conversation last update timestamp |

**Constraints:**
- FK: user_id REFERENCES "user"(id) ON DELETE SET NULL

**Indexes:**
- `idx_ai_conversation_user_id` ON (user_id)

---

## Table 19: AIMessage

_Description: Tracks AI Chatbot message history._

| Field Name | Data Type | Key | Allow Null | Description |
|------------|-----------|-----|------------|-------------|
| id | UUID | PK | No | Unique identifier for each message |
| conversation_id | UUID | FK | No | References AIConversation(id) |
| role | VARCHAR(20) | - | No | Message role: user, assistant, system |
| content | TEXT | - | No | Content of the message |
| created_at | TIMESTAMPTZ | - | No | Message created timestamp |

**Constraints:**
- `chk_ai_role`: role IN ('user','assistant','system')
- FK: conversation_id REFERENCES ai_conversation(id) ON DELETE CASCADE

**Indexes:**
- `idx_ai_message_conversation_id` ON (conversation_id)

---

## Table 20: AIUsage

_Description: Tracks AI usage per user per period._

| Field Name | Data Type | Key | Allow Null | Description |
|------------|-----------|-----|------------|-------------|
| id | UUID | PK | No | Unique identifier for each usage record |
| user_id | UUID | FK | Yes | References User(id) (Null for guest users) |
| request_count | INT | - | No | Total request count in usage period |
| period_start | TIMESTAMPTZ | - | No | Timestamp when the usage period starts |
| period_end | TIMESTAMPTZ | - | No | Timestamp when the usage period ends |

**Constraints:**
- FK: user_id REFERENCES "user"(id) ON DELETE SET NULL
- `uq_ai_usage_user_period`: UNIQUE (user_id, period_start, period_end)

**Indexes:**
- `idx_ai_usage_user_id` ON (user_id)

---

## Naming Conventions

| Element | Convention |
|---------|------------|
| Primary Key column | `id` (UUID, `gen_random_uuid()`) |
| Foreign Key column | `{referenced_table}_id` (e.g., `user_id`, `dish_id`) |
| Junction table PK | Composite `(table1_id, table2_id)` |
| Timestamps | `created_at`, `updated_at` (TIMESTAMPTZ, DEFAULT NOW()) |
| Soft delete | `deleted_at TIMESTAMPTZ` + partial index `WHERE deleted_at IS NULL` |
| CHECK constraints | `chk_{table}_{column}` (e.g., `chk_user_role`) |
| UNIQUE constraints | `uq_{table}_{columns}` (e.g., `uq_vote_user_post`) |
| Foreign Key constraints | `fk_{child}_{parent}` (e.g., `fk_post_user`) |

---

## Data Types (PostgreSQL Best Practices)

| Use Case | Type |
|----------|------|
| IDs | UUID with `gen_random_uuid()` |
| Nutrition macros | DECIMAL(10,2) |
| Small quantities | DECIMAL(10,3) |
| Coordinates | DECIMAL(9,6) (~0.11m precision) |
| Time durations | INTEGER minutes (`prep_time_minutes`) |
| Enum-like values | VARCHAR + CHECK constraint |
| JSON data | JSONB with GIN index |
| Large text | TEXT |
| URLs | TEXT |

---

## Table 21: Refresh Token

_Description: Stores SHA-256 hashes of opaque refresh tokens for JWT token refresh flow. Enables revocation and expiration tracking without storing raw tokens._

| Field Name | Data Type | Key | Allow Null | Description |
|------------|-----------|-----|------------|-------------|
| id | UUID | PK | No | Unique identifier for each refresh token record |
| user_id | UUID | FK | No | References "user"(id) ON DELETE CASCADE |
| token_hash | CHAR(64) | Unique | No | SHA-256 hash of the raw refresh token (hex encoded) |
| expires_at | TIMESTAMPTZ | - | No | Token expiration timestamp |
| revoked_at | TIMESTAMPTZ | - | Yes | Revocation timestamp (NULL = active) |
| created_at | TIMESTAMPTZ | - | No | Timestamp when token was issued |

**Constraints:**
- FK: user_id REFERENCES "user"(id) ON DELETE CASCADE
- `uq_refresh_token_hash`: UNIQUE (token_hash)

**Indexes:**
- `idx_refresh_token_user_id` ON (user_id)
- `idx_refresh_token_hash` ON (token_hash)
- `idx_refresh_token_expires` ON (expires_at)

---

## Table 22: Blacklist Token

_Description: Access token denylist for immediate revocation. Stores JWT ID (jti) and issuer from revoked access tokens. Entry persists until token's natural expiration._

| Field Name | Data Type | Key | Allow Null | Description |
|------------|-----------|-----|------------|-------------|
| jti | VARCHAR(36) | PK (part 1) | No | JWT ID from revoked access token |
| issuer | VARCHAR(100) | PK (part 2) | No | JWT issuer claim from revoked access token |
| expires_at | TIMESTAMPTZ | - | No | Original JWT expiration timestamp |
| revoked_at | TIMESTAMPTZ | - | No | Timestamp when token was blacklisted (DEFAULT NOW()) |

**Constraints:**
- Primary Key: (jti, issuer)

**Indexes:**
- `idx_blacklist_token_expires` ON (expires_at)

---

## Table 23: Password Reset OTP

_Description: Stores SHA-256 hashes of 6-digit password-reset codes for the forgot-password flow. One row per issued code; at most one unused, unexpired row is active per user._

| Field Name | Data Type | Key | Allow Null | Description |
|------------|-----------|-----|------------|-------------|
| id | UUID | PK | No | Unique identifier for each OTP record |
| user_id | UUID | FK | No | References "user"(id) - Account the code was issued for |
| otp_hash | CHAR(64) | - | No | SHA-256 hex hash of the raw 6-digit code (raw code never stored) |
| expires_at | TIMESTAMPTZ | - | No | Code expiration timestamp (issued_at + 10 minutes) |
| used_at | TIMESTAMPTZ | - | Yes | When the code was consumed or superseded (NULL = active) |
| created_at | TIMESTAMPTZ | - | No | Timestamp when the code was issued |

**Constraints:**
- FK: user_id REFERENCES "user"(id) ON DELETE CASCADE

**Indexes:**
- `idx_password_reset_otp_user_id` ON (user_id)
- `idx_password_reset_otp_expires` ON (expires_at)

---

## Relationship Summary

- **User** 1:N **UserProfile** (1:1 via unique FK)
- **User** 1:N **Post** (author)
- **User** 1:N **Comment**
- **User** 1:N **Vote**
- **User** 1:N **Recipe** (author)
- **User** 1:N **Menu**
- **User** 1:N **AIConversation** (nullable for guests)
- **User** 1:N **AIUsage** (nullable for guests)
- **User** 1:N **RefreshToken** (auth tokens for session management)
- **User** 1:N **PasswordResetOtp** (password-reset codes; at most one active per user)
- **Post** M:N **Category** (via Post_Category)
- **Post** M:N **Media** (via Post_Media)
- **Post** M:N **Recipe** (via Post_Recipe)
- **Post** N:1 **Location** (tagged location)
- **Comment** N:1 **Post**, N:1 **Comment** (parent)
- **Vote** N:1 **Post**, N:1 **User** (unique per user+post)
- **Recipe** N:1 **Dish** (variations of a canonical dish)
- **Recipe** N:1 **User** (author)
- **Recipe** M:N **Ingredient** (via Recipe_Ingredient)
- **Menu** N:1 **User**
- **MenuDetail** N:1 **Menu**, N:1 **Dish** (unique per menu+date+meal_type)
- **Location** 1:N **Post** (via location_id)
- **AIConversation** N:1 **User** (nullable)
- **AIMessage** N:1 **AIConversation**
- **AIUsage** N:1 **User** (nullable)
- **BlacklistToken** — No direct FK (identified by JWT jti+issuer only)