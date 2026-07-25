package com.osrsflipfinder.runelite;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/** Builds the GE copilot sidebar body for a resolved {@link FlipOpportunity}. */
final class GeCopilotUi
{
	private GeCopilotUi()
	{
	}

	static JPanel buildBody(ItemManager itemManager, int itemId, FlipOpportunity opp)
	{
		JPanel root = new JPanel();
		root.setLayout(new BoxLayout(root, BoxLayout.Y_AXIS));
		PluginUi.transparent(root);
		root.setAlignmentX(JPanel.LEFT_ALIGNMENT);

		FlipCopilotPresenter.Verdict verdict = FlipCopilotPresenter.verdict(opp);

		root.add(itemHeader(itemManager, itemId, opp.getName()));
		root.add(PluginUi.gap(PluginUi.SPACING_SM));
		root.add(verdictBadge(verdict));
		root.add(PluginUi.gap(PluginUi.SPACING_SM));
		root.add(heroMetrics(opp));
		root.add(PluginUi.gap(PluginUi.SPACING_SM));
		root.add(priceCard(opp));
		root.add(PluginUi.gap(PluginUi.SPACING_SM));
		root.add(flipSizeCard(opp));
		root.add(PluginUi.gap(PluginUi.SPACING_SM));
		root.add(speedCard(opp));
		root.add(PluginUi.gap(PluginUi.SPACING_SM));
		root.add(scoreCard(opp, verdict));

		String hint = FlipCopilotPresenter.repriceHint(opp);
		if (hint != null)
		{
			root.add(PluginUi.gap(PluginUi.SPACING_XS));
			root.add(callout(hint, PluginUi.WARNING));
		}
		if (opp.isPriceDumped())
		{
			root.add(PluginUi.gap(PluginUi.SPACING_XS));
			root.add(callout("Dump flag: price crashed vs recent hourly norms.", PluginUi.NEGATIVE));
		}

		root.add(PluginUi.gap(PluginUi.SPACING_XS));
		root.add(PluginUi.wrappedCaption(FlipCopilotPresenter.actionSummary(opp)));

		SidebarContentPanel.lockWidth(root);
		return root;
	}

	private static JPanel itemHeader(ItemManager itemManager, int itemId, String name)
	{
		JPanel card = PluginUi.card();
		card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
		JLabel icon = new JLabel();
		icon.setPreferredSize(new Dimension(32, 28));
		icon.setAlignmentX(Component.LEFT_ALIGNMENT);
		itemManager.getImage(itemId).addTo(icon);
		String safe = name != null ? name : "Item";
		JLabel title = PluginUi.wrappedBody(safe, PluginUi.cardBodyWrapWidth(), Color.WHITE, true);
		title.setAlignmentX(Component.LEFT_ALIGNMENT);
		title.setToolTipText(safe.length() > 40 ? safe : null);
		card.add(icon);
		card.add(PluginUi.gap(PluginUi.SPACING_XS));
		card.add(title);
		return card;
	}

	private static JPanel verdictBadge(FlipCopilotPresenter.Verdict verdict)
	{
		JLabel badge = new JLabel(FlipCopilotPresenter.verdictLabel(verdict), JLabel.CENTER);
		Color color = FlipCopilotPresenter.verdictColor(verdict);
		badge.setForeground(color);
		badge.setFont(FontManager.getRunescapeBoldFont());
		badge.setBorder(javax.swing.BorderFactory.createCompoundBorder(
			javax.swing.BorderFactory.createLineBorder(color.darker(), 1, true),
			new javax.swing.border.EmptyBorder(4, 8, 4, 8)
		));

		JPanel wrap = new SidebarContentPanel();
		wrap.setLayout(new BorderLayout());
		PluginUi.transparent(wrap);
		wrap.add(badge, BorderLayout.CENTER);
		SidebarContentPanel.lockWidthFixed(wrap, 28);
		return wrap;
	}

	private static JPanel heroMetrics(FlipOpportunity opp)
	{
		JLabel net = new JLabel();
		long netGp = opp.getNetProfitPerItem();
		net.setForeground(netGp >= 0 ? PluginUi.POSITIVE : PluginUi.NEGATIVE);
		PluginUi.setHeroGridStatValue(
			net,
			MarketFormat.signedGp(netGp),
			null
		);

		JLabel roi = new JLabel();
		roi.setForeground(Color.WHITE);
		PluginUi.setHeroGridStatValue(
			roi,
			MarketFormat.percent(opp.getNetRoiPercent()),
			null
		);

		JLabel gpHr = new JLabel();
		gpHr.setForeground(PluginUi.GOLD);
		long hr = opp.getEstimatedProfitPerHour();
		PluginUi.setHeroGridStatValue(
			gpHr,
			MarketFormat.gp(hr),
			null
		);

		JLabel profit30 = new JLabel();
		long p30 = opp.getEstimatedProfit30m();
		profit30.setForeground(p30 >= 0 ? PluginUi.POSITIVE : PluginUi.NEGATIVE);
		PluginUi.setHeroGridStatValue(
			profit30,
			MarketFormat.signedGp(p30),
			null
		);

		return PluginUi.detailHeroGrid(
			PluginUi.statCell(net, "Net / item"),
			PluginUi.statCell(roi, "Net ROI"),
			PluginUi.statCell(gpHr, "GP / hr"),
			PluginUi.statCell(profit30, "~30m profit")
		);
	}

	private static JPanel priceCard(FlipOpportunity opp)
	{
		JPanel block = PluginUi.statBlock();
		PluginUi.addStatLine(block, "Buy offer price", MarketFormat.gp(opp.getEstimatedBuyPrice()),
			FlipCopilotPresenter.buyPriceColor());
		PluginUi.addStatLine(block, "Sell offer price", MarketFormat.gp(opp.getEstimatedSellPrice()),
			FlipCopilotPresenter.sellPriceColor());
		long spread = opp.getEstimatedSellPrice() - opp.getEstimatedBuyPrice();
		PluginUi.addStatLine(block, "Gross spread", MarketFormat.gp(spread), ColorScheme.LIGHT_GRAY_COLOR);
		PluginUi.finalizeStatBlock(block);

		return labeledSection("Prices to enter", block);
	}

	private static JPanel flipSizeCard(FlipOpportunity opp)
	{
		JPanel block = PluginUi.statBlock();
		PluginUi.addStatLine(block, "Qty this flip", MarketFormat.qtyLimit(
			opp.getEstimatedTradableQuantity(), opp.getBuyLimit()));
		PluginUi.addStatLine(block, "Capital needed", MarketFormat.gp(opp.getEstimatedCapitalRequired()));
		PluginUi.addStatLine(block, "Profit at qty", MarketFormat.signedGp(opp.getEstimatedProfitAtQuantity()),
			opp.getEstimatedProfitAtQuantity() >= 0 ? PluginUi.POSITIVE : PluginUi.NEGATIVE);
		PluginUi.finalizeStatBlock(block);
		return labeledSection("Flip size", block);
	}

	private static JPanel speedCard(FlipOpportunity opp)
	{
		JPanel block = PluginUi.statBlock();
		PluginUi.addStatLine(block, "Fill time", FlipCopilotPresenter.formatTurnover(opp.getEstimatedTurnoverHours()));
		PluginUi.addStatLine(block, "Vol 5m", MarketFormat.gp(opp.getFiveMinuteVolume()));
		PluginUi.addStatLine(block, "Vol 1h", MarketFormat.gp(opp.getOneHourVolume()));
		PluginUi.finalizeStatBlock(block);
		return labeledSection("Liquidity", block);
	}

	private static JPanel scoreCard(FlipOpportunity opp, FlipCopilotPresenter.Verdict verdict)
	{
		JPanel block = PluginUi.statBlock();
		PluginUi.addStatLine(block, "Rank", FlipCopilotPresenter.scoreLine(opp), FlipCopilotPresenter.verdictColor(verdict));
		PluginUi.addStatLine(block, "Strength", FlipCopilotPresenter.scoreBar(opp), PluginUi.GOLD_DIM);
		PluginUi.finalizeStatBlock(block);
		return labeledSection("Flip score", block);
	}

	private static JPanel labeledSection(String title, JPanel body)
	{
		JPanel wrap = new SidebarContentPanel();
		wrap.setLayout(new BoxLayout(wrap, BoxLayout.Y_AXIS));
		PluginUi.transparent(wrap);
		wrap.add(PluginUi.sectionHeader(title));
		wrap.add(body);
		SidebarContentPanel.lockWidth(wrap);
		return wrap;
	}

	private static JPanel callout(String text, Color color)
	{
		JPanel row = PluginUi.card();
		JLabel label = PluginUi.wrappedCaption(text);
		label.setForeground(color);
		row.add(label);
		PluginUi.fullWidth(row);
		return row;
	}
}
