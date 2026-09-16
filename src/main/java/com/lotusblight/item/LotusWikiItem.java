package com.lotusblight.item;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.WrittenBookItem;
import net.minecraft.world.level.Level;

public class LotusWikiItem extends WrittenBookItem {
    public LotusWikiItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!stack.hasTag() || !stack.getTag().contains("pages")) {
            CompoundTag generated = createStack().getTag();
            if (generated != null) stack.getOrCreateTag().merge(generated);
        }
        return super.use(level, player, hand);
    }

    public static ItemStack createStack() {
        ItemStack stack = new ItemStack(com.lotusblight.registry.ModItems.LOTUS_WIKI.get());
        CompoundTag tag = stack.getOrCreateTag();
        tag.putString("title", "Вики Lotus Blight");
        tag.putString("author", "Коллектив лотоса");
        ListTag pages = new ListTag();
        pages.add(page("LOTUS BLIGHT\n\nТри семени уже у тебя. Ты сам решаешь, куда пустить корни."));
        pages.add(page("ПОСАДКА\n\nИспользуй семя по источнику воды. Лотос появится сверху, как кувшинка. Вода не заменяется. В глубоких водоёмах рядом появятся подводные корни."));
        pages.add(page("ФАЗЫ\n\n1 Цветение — заражается вода.\n2 Захват реки — очаг следует по руслу.\n3 Захват ближников — заражаются земля и деревья.\n4 Мини-биом — заражение продолжает расти."));
        pages.add(page("МИМИКИ\n\nНекоторые кувшинки — ловушки. Наступишь на такую — получишь Лотонирию. Следи за странными оттенками, пульсацией и частицами."));
        pages.add(page("ЛОТОНИРИЯ\n\nЭффект путает инвентарь и пытается выбросить очищающий порошок. Не носи все средства очищения в одном месте."));
        pages.add(page("БЛЕСК\n\nХолодный антирастительный край. Его нодули опасны, а местная растительность редка. Блеск замедляет распространение лотоса."));
        pages.add(page("АТЛАС\n\nM скрывает мини-карту. N открывает Atlas. Карта показывает воду, лотосы и заражённые участки из кэшированных данных."));
        pages.add(page("ВЫЖИВАНИЕ\n\nНе наступай на кувшинки с неправильным оттенком или странной пульсацией. Увидел зелёные частицы под водой — отойди от корней."));
        pages.add(page("ИСЦЕЛЕНИЕ\n\nИщи главный якорь и корни под водой. Блеск может остановить рост, а очищающий порошок работает лучше до превращения очага в мини-биом."));
        pages.add(page("ДВЕ СТОРОНЫ\n\nКоллектив предложит присоединиться и распространять лотос. Ботаники и хранители Блеска помогут свергнуть главный якорь. Выбор изменит реплики и награды."));
        tag.put("pages", pages);
        return stack;
    }

    private static StringTag page(String text) {
        return StringTag.valueOf(Component.Serializer.toJson(Component.literal(text)));
    }
}
