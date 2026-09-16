# Визуальная совместимость Lotus Blight

## Streams Reflowing

Страница Modrinth: https://modrinth.com/mod/streams-reflowing/versions

На странице указано, что Streams Reflowing поддерживает Minecraft 1.20.1–1.20.6, платформы Fabric, Forge и NeoForge, а также клиент и сервер. Поэтому целевой набор Forge 1.20.1 совместим с указанным модом. Для Lotus Blight безопаснее использовать мягкую совместимость: реагировать на фактически существующие блоки воды и направление русла, не добавляя жёсткую зависимость от внутреннего API Streams Reflowing.

## Complementary Shaders

Официальная страница: https://www.complementary.dev/shaders/

Complementary описывает себя как шейдерпак с высоким качеством, детализацией и производительностью. Доступны два стиля: Unbound рассчитан на более реалистичное изображение, а Reimagined — на переосмысление Minecraft с сохранением узнаваемого ванильного стиля. Официальная страница отмечает, что оба стиля можно настраивать после установки, включая реалистичную воду в Reimagined.

## Предварительный выбор

Для Lotus Blight лучше начать с Complementary Reimagined. Он должен сохранить читаемость зелёной листвы и розовых лепестков, а настройка воды и свечения поможет подчеркнуть цветущие реки. В моде стоит использовать умеренную яркость частиц, нефритово-зелёную заражённую почву, розовые цветы и слабое розовое свечение Сердца лотоса. Не следует полагаться только на шейдер: основные цвета должны быть видимы и без него.

## References

[1]: https://modrinth.com/mod/streams-reflowing/versions — Streams Reflowing, страница версий Modrinth.
[2]: https://www.complementary.dev/shaders/ — Complementary Shaders, официальный сайт.

## Forge shader loader

Страница Oculus на Modrinth: https://modrinth.com/mod/oculus

Oculus описан как неофициальная ветка Iris, адаптированная для FML. На странице указана поддержка Minecraft 1.20–1.20.1, платформ Forge и NeoForge, клиентская сторона. Также заявлена совместимость с существующими ShaderMod/OptiFine shader packs без изменений.

Complementary Reimagined на Modrinth отмечен как совместимый с Minecraft 1.20.x и с Iris/OptiFine; его теги включают Colored Lighting, Atmosphere, Bloom, Foliage, Reflections и Shadows. Практическая связка для Forge 1.20.1: Oculus на клиенте + Complementary Reimagined как shader pack.

## References

[3]: https://modrinth.com/mod/oculus — Oculus, страница Modrinth.
[4]: https://modrinth.com/shader/complementary-reimagined/versions — Complementary Reimagined, версии и совместимость на Modrinth.
