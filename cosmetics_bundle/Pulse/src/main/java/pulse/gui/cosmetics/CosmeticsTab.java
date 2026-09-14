package pulse.gui.cosmetics;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import org.joml.Matrix3x2fStack;
import pulse.config.LocalConfigManager;
import pulse.cosmetic.LocalCosmetics;
import pulse.gui.core.ClickGuiTab;
import pulse.gui.core.ClickGuiTabType;
import pulse.gui.core.GuiInput;
import pulse.gui.core.GuiLayerRegistry;
import pulse.gui.core.PulseClickGuiScreen;
import pulse.gui.core.TabHost;
import pulse.gui.core.TabSelector;
import pulse.gui.widgets.ScrollBar;
import pulse.render.Renderer2D;
import pulse.render.font.FontManager;
import pulse.render.font.FontRenderer;
import pulse.theme.Theme;

public class CosmeticsTab implements TabHost, ClickGuiTab {
   private static final String[] TABS = new String[]{"All", "Capes", "Wings", "Body", "Pets", "Hats"};
   private static final String[] TYPES = new String[]{"", "cape", "wings", "bodywear", "pet", "hat"};
   private static final float TOP = 48.0F;
   private static final float LEFT = 19.0F;
   private static final float GAP = 6.0F;
   private static final float CARD_W = 85.0F;
   private static final float CARD_H = 51.0F;
   private static final int COLUMNS = 4;
   private final TabSelector tabs = new TabSelector(this);
   private final ScrollBar scroll = new ScrollBar(2.0F, 18.0F);
   private int tab;
   private int mouseX;
   private int mouseY;
   private float contentHeight;
   private float viewportHeight;

   @Override
   public String[] c() {
      return TABS;
   }

   @Override
   public int d() {
      return this.tab;
   }

   @Override
   public void a(int i) {
      if (i >= 0 && i < TABS.length && i != this.tab) {
         this.tab = i;
         this.scroll.e(0.0F);
      }
   }

   @Override
   public void a(Matrix3x2fStack matrices, Renderer2D renderer, float x, float y, int mouseX, int mouseY) {
      this.mouseX = mouseX;
      this.mouseY = mouseY;
      this.tabs.a(matrices, renderer, x, y, mouseX, mouseY);

      float width = PulseClickGuiScreen.d();
      float height = PulseClickGuiScreen.e();
      float gridX = x + LEFT;
      float gridY = y + TOP;
      float gridW = width - LEFT * 2.0F;
      float gridH = height - TOP - PulseClickGuiScreen.f();
      List<Integer> items = this.filtered();
      this.contentHeight = this.contentHeight(items.size());
      this.viewportHeight = gridH;
      this.scroll.a();
      this.scroll.b(this.contentHeight, this.viewportHeight);

      this.renderHeader(matrices, renderer, x, y, width);
      renderer.b().a(gridX - 1.0F, gridY - 1.0F, gridW + 2.0F, gridH + 2.0F, matrices);
      this.renderCards(matrices, renderer, gridX, gridY, mouseX, mouseY, items);
      renderer.b().a(matrices);
      this.renderScroll(matrices, renderer, gridX, gridY, gridW, gridH, mouseX, mouseY);
   }

   private void renderHeader(Matrix3x2fStack matrices, Renderer2D renderer, float x, float y, float width) {
      FontRenderer title = FontManager.b[18];
      FontRenderer meta = FontManager.a[14];
      int equipped = LocalCosmetics.selectedIndices().size();
      String count = equipped + "/5 equipped";
      float countW = meta.a(count);
      renderer.a(x + width - 20.0F - countW - 12.0F, y + 17.0F, countW + 12.0F, 12.0F, 6.0F, Theme.a(Theme.C, 45), matrices);
      title.a("Cosmetics", x + 252.0F, y + 17.0F, Theme.aa, matrices);
      meta.a(count, x + width - 20.0F - countW - 6.0F, y + 20.0F, Theme.b, matrices);
   }

   private void renderCards(Matrix3x2fStack matrices, Renderer2D renderer, float x, float y, int mouseX, int mouseY, List<Integer> items) {
      FontRenderer nameFont = FontManager.b[14];
      FontRenderer metaFont = FontManager.a[12];
      float offset = this.scroll.b();

      for (int pos = 0; pos < items.size(); pos++) {
         int index = items.get(pos);
         int col = pos % COLUMNS;
         int row = pos / COLUMNS;
         float cardX = x + col * (CARD_W + GAP);
         float cardY = y + row * (CARD_H + GAP) - offset;
         if (cardY > y + this.viewportHeight || cardY + CARD_H < y) {
            continue;
         }

         boolean hovered = GuiInput.a(cardX, cardY, CARD_W, CARD_H, mouseX, mouseY);
         boolean selected = LocalCosmetics.isSelected(index);
         Color bg = selected ? Theme.a(Theme.C, 70) : (hovered ? Theme.r : Theme.q);
         Color border = selected ? Theme.C : Theme.a(Theme.aa, hovered ? 45 : 18);
         renderer.a(cardX, cardY, CARD_W, CARD_H, 6.0F, bg, matrices);
         renderer.b(cardX, cardY, CARD_W, CARD_H, 6.0F, 0.7F, border, matrices);
         renderer.a(LocalCosmetics.texture(index), cardX + 7.0F, cardY + 8.0F, 23.0F, 23.0F, Color.WHITE, matrices);

         String name = LocalCosmetics.name(index);
         nameFont.a(name, cardX + 35.0F, cardY + 9.0F, Theme.aa, matrices);
         metaFont.a(this.displayType(LocalCosmetics.type(index)), cardX + 35.0F, cardY + 23.0F, Theme.b, matrices);
         renderer.a(cardX + 7.0F, cardY + 37.0F, CARD_W - 14.0F, 8.0F, 4.0F, selected ? Theme.X : Theme.a(Theme.aa, hovered ? 34 : 18), matrices);
         String action = selected ? "ON" : "EQUIP";
         float actionW = metaFont.a(action);
         metaFont.a(action, cardX + (CARD_W - actionW) / 2.0F, cardY + 37.5F, selected ? Color.WHITE : Theme.b, matrices);
      }
   }

   private void renderScroll(Matrix3x2fStack matrices, Renderer2D renderer, float x, float y, float width, float height, int mouseX, int mouseY) {
      if (this.contentHeight > this.viewportHeight) {
         renderer.a(x + width - 3.0F, y, 2.0F, height, 1.0F, Theme.a(Theme.aa, 18), matrices);
         this.scroll.a(matrices, renderer, x + width - 4.0F, y, height, this.contentHeight, this.viewportHeight, mouseX, mouseY, false);
      }
   }

   private List<Integer> filtered() {
      String type = TYPES[this.tab];
      List<Integer> result = new ArrayList<>();

      for (int i = 0; i < LocalCosmetics.size(); i++) {
         if (type.isEmpty() || type.equals(LocalCosmetics.type(i))) {
            result.add(i);
         }
      }

      return result;
   }

   private float contentHeight(int count) {
      int rows = Math.max(1, (count + COLUMNS - 1) / COLUMNS);
      return rows * CARD_H + (rows - 1) * GAP;
   }

   private String displayType(String type) {
      if ("bodywear".equals(type)) {
         return "body";
      } else {
         return type;
      }
   }

   private int hitCard(float x, float y, int mouseX, int mouseY) {
      if (!GuiInput.a(x, y, PulseClickGuiScreen.d() - LEFT * 2.0F, this.viewportHeight, mouseX, mouseY)) {
         return -1;
      }

      List<Integer> items = this.filtered();
      float localX = mouseX - x;
      float localY = mouseY - y + this.scroll.b();
      int col = (int)(localX / (CARD_W + GAP));
      int row = (int)(localY / (CARD_H + GAP));
      if (col < 0 || col >= COLUMNS) {
         return -1;
      }

      float inCellX = localX - col * (CARD_W + GAP);
      float inCellY = localY - row * (CARD_H + GAP);
      int pos = row * COLUMNS + col;
      return inCellX <= CARD_W && inCellY <= CARD_H && pos >= 0 && pos < items.size() ? items.get(pos) : -1;
   }

   @Override
   public void a(float x, float y, int mouseX, int mouseY) {
      this.tabs.a(x, y, mouseX, mouseY);
      float gridX = x + LEFT;
      float gridY = y + TOP;
      int hit = this.hitCard(gridX, gridY, mouseX, mouseY);
      if (hit >= 0) {
         LocalCosmetics.toggle(hit);
         LocalConfigManager.get().requestSave("cosmetic-toggle");
      } else {
         this.scroll.a(gridX + PulseClickGuiScreen.d() - LEFT * 2.0F - 4.0F, gridY, this.viewportHeight, this.contentHeight, this.viewportHeight, mouseX, mouseY);
      }
   }

   @Override
   public void b(float x, float y, int mouseX, int mouseY) {
      this.a(x, y, mouseX, mouseY);
   }

   @Override
   public void c(float x, float y, int mouseX, int mouseY) {
      this.scroll.d();
   }

   @Override
   public void a(float x, float y, int mouseX, int mouseY, double deltaX, double deltaY) {
      this.scroll.a(mouseY, this.contentHeight, this.viewportHeight);
   }

   @Override
   public void a(float amount) {
      this.scroll.a(amount, this.contentHeight, this.viewportHeight);
   }

   @Override
   public ClickGuiTabType a() {
      return ClickGuiTabType.COSMETICS;
   }
}
