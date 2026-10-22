-- Видаляє тестові дані, створені seed-10000-profiles.sql (користувачі load…@test.ua).
-- Ключові слова залишаються в довіднику.
-- Запрошення за участю тестових користувачів разом з їхніми результатами сумісності
with deleted as (
    delete from invitations i
    using users u
    where u.id in (i.sender_id, i.receiver_id)
      and u.email like 'load%@test.ua'
    returning i.destiny_matrix_result_id
)
delete from destiny_matrix_results r
where r.id in (select destiny_matrix_result_id from deleted);

delete from profile_keywords pk
using profiles p, users u
where pk.profile_id = p.id
  and p.user_id = u.id
  and u.email like 'load%@test.ua';

delete from profiles p
using users u
where p.user_id = u.id
  and u.email like 'load%@test.ua';

delete from users
where email like 'load%@test.ua';
