-- =====================================================================
-- Тестові дані для перевірки НФВ продуктивності (ЛР1):
-- «завантаження сторінки анкети та результатів пошуку — до 2 секунд
--  при базі до 10 000 анкет».
--
-- Створює 10 000 користувачів load1@test.ua … load10000@test.ua
-- з паролем password123, анкетами та 3 випадковими ключовими словами в кожній.
-- Скрипт можна запускати повторно: наявні записи не дублюються.
--
-- Запуск: pgAdmin → база dating_app → Query Tool → відкрити цей файл → Execute (F5).
-- Таблиці мають уже існувати: спершу хоча б раз запустіть застосунок.
-- Видалити тестові дані: docs/sql/delete-test-profiles.sql
-- =====================================================================

-- 1. Довідник ключових слів
insert into keywords (tag)
select tag
from unnest(array[
    'музика', 'подорожі', 'кава', 'книги', 'кіно', 'спорт', 'йога', 'біг', 'походи', 'гітара',
    'фотографія', 'малювання', 'танці', 'кулінарія', 'театр', 'настолки', 'ігри', 'програмування',
    'велосипед', 'плавання', 'тварини', 'собаки', 'коти', 'вино', 'джаз', 'рок', 'аніме', 'мови',
    'волонтерство', 'наука'
]) as tag
on conflict (tag) do nothing;

-- 2. Облікові записи. Хеш BCrypt (10 раундів) для пароля password123
insert into users (email, password_hash, is_active, created_at)
select 'load' || n || '@test.ua',
       '$2a$10$A9mFuH/m9ucexeKOJiFC6ujXfdL7EPYguY1ZNYedlcvD/NWv0/1Yi',
       true,
       now()
from generate_series(1, 10000) as n
on conflict (email) do nothing;

-- 3. Анкети: вік від 18 до 50 років, без закритих даних і прихованих полів
insert into profiles (user_id, first_name, birth_date, open_description, photos_private, age_private)
select u.id,
       (array['Анна', 'Олег', 'Марія', 'Іван', 'Софія', 'Дмитро', 'Катерина', 'Андрій', 'Юлія', 'Максим'])[1 + (u.id % 10)::int],
       current_date - (interval '18 years') - (floor(random() * 32 * 365) || ' days')::interval,
       'Тестова анкета для навантажувального тестування',
       false,
       false
from users u
where u.email like 'load%@test.ua'
  and not exists (select 1 from profiles p where p.user_id = u.id);

-- 4. По 3 випадкових ключових слова на анкету
insert into profile_keywords (profile_id, keyword_id)
select p.id, k.id
from profiles p
join users u on u.id = p.user_id
cross join lateral (
    select kw.id
    from keywords kw
    where p.id is not null          -- змушує обирати теги окремо для кожної анкети
    order by random()
    limit 3
) k
where u.email like 'load%@test.ua'
  and not exists (select 1 from profile_keywords pk where pk.profile_id = p.id)
on conflict do nothing;

-- 5. Оновити статистику, щоб PostgreSQL одразу використовував індекси
analyze users;
analyze profiles;
analyze keywords;
analyze profile_keywords;

-- Перевірка: має бути 10000
select count(*) as test_profiles
from profiles p
join users u on u.id = p.user_id
where u.email like 'load%@test.ua';
