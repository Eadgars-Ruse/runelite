/*
 * Copyright (c) 2018, Eadgars Ruse <https://github.com/Eadgars-Ruse>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package net.runelite.client.plugins.questtab;

import com.google.common.eventbus.Subscribe;
import com.google.inject.Provides;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import javax.inject.Inject;
import lombok.Getter;
import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.ScriptID;
import net.runelite.api.events.ConfigChanged;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.PlayerMenuOptionClicked;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetID;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import org.apache.commons.lang3.ArrayUtils;

@PluginDescriptor(
	name = "Quest List Filtering"
)
public class QuestTabPlugin extends Plugin
{
	@Inject
	private ClientThread clientThread;

	@Inject
	private Client client;

	@Inject
	private QuestTabConfig config;

	@Inject
	private ConfigManager configManager;

	@Provides
	QuestTabConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(QuestTabConfig.class);
	}

	// Ways the quest list can be sorted
	private enum SortType
	{
		ALPHABETICAL,
		DIFFICULTY,
		LENGTH
	}

	public enum QuestStatus
	{
		COMPLETE,
		IN_PROGRESS,
		NOT_STARTED
	}

	@Getter
	private HashMap<Quest, QuestStatus> questStatus = new HashMap<>();

	private LinkedHashMap<Widget, Quest> freeWidgets = new LinkedHashMap<>();
	private LinkedHashMap<Widget, Quest> membersWidgets = new LinkedHashMap<>();
	private LinkedHashMap<Widget, Quest> miniquestWidgets = new LinkedHashMap<>();

	private static final String CONFIG_GROUP = "questtab";

	private static final int QUEST_LIST_TITLE_SPACE = 20;
	private static final int QUEST_LIST_ITEM_SPACE = 15;
	private static final int QUEST_LIST_TOP_PADDING = 10;
	private static final int QUEST_LIST_BOTTOM_PADDING = 8;

	private static final int COMPLETE_COLOR = 901389;
	private static final int IN_PROGRESS_COLOR = 16776960;
	private static final int NOT_STARTED_COLOR = 16711680;

	private static final String SHOW_FREE = "Show Free";
	private static final String SHOW_MEMBERS = "Show Members";
	private static final String SHOW_MINIQUESTS = "Show Miniquests";
	private static final String SHOW_COMPLETE = "Show Complete";
	private static final String SHOW_IN_PROGRESS = "Show In-Progress";
	private static final String SHOW_NOT_STARTED = "Show Not-Started";
	private static final String SHOW_CANT_DO = "Show Can't-Do";
	private static final String SHOW_DIFFICULTY = "Show Difficulty";
	private static final String SORT_BY_DIFFICULTY = "Sort by Difficulty";
	private static final String SHOW_LENGTH = "Show Length";
	private static final String SORT_BY_LENGTH = "Sort by Length";
	private static final String HIDE_FREE = "Hide Free";
	private static final String HIDE_MEMBERS = "Hide Members";
	private static final String HIDE_MINIQUESTS = "Hide Miniquests";
	private static final String HIDE_COMPLETE = "Hide Complete";
	private static final String HIDE_IN_PROGRESS = "Hide In-Progress";
	private static final String HIDE_NOT_STARTED = "Hide Not-Started";
	private static final String HIDE_CANT_DO = "Hide Can't-Do";
	private static final String HIDE_DIFFICULTY = "Hide Difficulty";
	private static final String UNSORT_BY_DIFFICULTY = "Unsort by Difficulty";
	private static final String HIDE_LENGTH = "Hide Length";
	private static final String UNSORT_BY_LENGTH = "Unsort by Length";
	private static final String RESET = "Reset";

	@Subscribe
	public void onConfigChanged(ConfigChanged e)
	{
		if (e.getGroup().equals(CONFIG_GROUP))
		{
			clientThread.invokeLater(() -> client.runScript(
				ScriptID.QUEST_LIST_INIT,
				WidgetInfo.QUEST_LIST_CONTROL.getId(),
				WidgetInfo.QUEST_LIST_LISTS.getId(),
				WidgetInfo.QUEST_LIST_SCROLLBAR.getId(),
				WidgetInfo.QUEST_LIST_QP.getId(),
				WidgetInfo.QUEST_LIST_FREE.getId(),
				WidgetInfo.QUEST_LIST_MEMBERS.getId(),
				WidgetInfo.QUEST_LIST_MINIQUESTS.getId()
			));
			clientThread.invokeLater(this::processQuestList);
		}
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded widgetLoaded)
	{
		if (widgetLoaded.getGroupId() == WidgetID.QUEST_LIST_GROUP_ID)
		{
			clientThread.invokeLater(() -> client.runScript(
				ScriptID.QUEST_LIST_INIT,
				WidgetInfo.QUEST_LIST_CONTROL.getId(),
				WidgetInfo.QUEST_LIST_LISTS.getId(),
				WidgetInfo.QUEST_LIST_SCROLLBAR.getId(),
				WidgetInfo.QUEST_LIST_QP.getId(),
				WidgetInfo.QUEST_LIST_FREE.getId(),
				WidgetInfo.QUEST_LIST_MEMBERS.getId(),
				WidgetInfo.QUEST_LIST_MINIQUESTS.getId()
			));
			clientThread.invokeLater(this::processQuestList);
		}
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		if (event.getWidgetId() == WidgetInfo.FIXED_VIEWPORT_QUESTS_TAB.getId() ||
			event.getWidgetId() == WidgetInfo.RESIZABLE_VIEWPORT_QUESTS_TAB.getId() ||
			event.getWidgetId() == WidgetInfo.RESIZABLE_VIEWPORT_BOTTOM_LINE_QUEST_TAB.getId())
		{
			clientThread.invokeLater(() -> client.runScript(
				ScriptID.QUEST_LIST_INIT,
				WidgetInfo.QUEST_LIST_CONTROL.getId(),
				WidgetInfo.QUEST_LIST_LISTS.getId(),
				WidgetInfo.QUEST_LIST_SCROLLBAR.getId(),
				WidgetInfo.QUEST_LIST_QP.getId(),
				WidgetInfo.QUEST_LIST_FREE.getId(),
				WidgetInfo.QUEST_LIST_MEMBERS.getId(),
				WidgetInfo.QUEST_LIST_MINIQUESTS.getId()
			));
			clientThread.invokeLater(this::processQuestList);
		}
	}

	private void processQuestList()
	{
		int overallYpos = QUEST_LIST_TOP_PADDING;
		freeWidgets.clear();

		if (config.hideFree())
		{
			hideListItems(WidgetInfo.QUEST_LIST_FREE);
		}
		else
		{
			showListItems(WidgetInfo.QUEST_LIST_FREE, freeWidgets);
			freeWidgets = sortListItems(WidgetInfo.QUEST_LIST_FREE, freeWidgets);
			overallYpos = processQuestListItems(WidgetInfo.QUEST_LIST_FREE, freeWidgets, overallYpos);
			overallYpos += QUEST_LIST_BOTTOM_PADDING;
		}

		membersWidgets.clear();

		if (config.hideMembers())
		{
			hideListItems(WidgetInfo.QUEST_LIST_MEMBERS);
		}
		else
		{
			showListItems(WidgetInfo.QUEST_LIST_MEMBERS, membersWidgets);
			membersWidgets = sortListItems(WidgetInfo.QUEST_LIST_MEMBERS, membersWidgets);
			overallYpos = processQuestListItems(WidgetInfo.QUEST_LIST_MEMBERS, membersWidgets, overallYpos);
		}

		miniquestWidgets.clear();

		if (config.hideMiniquests())
		{
			hideListItems(WidgetInfo.QUEST_LIST_MINIQUESTS);
		}
		else
		{
			showListItems(WidgetInfo.QUEST_LIST_MINIQUESTS, miniquestWidgets);
			miniquestWidgets = sortListItems(WidgetInfo.QUEST_LIST_MINIQUESTS, miniquestWidgets);
			overallYpos = processQuestListItems(WidgetInfo.QUEST_LIST_MINIQUESTS, miniquestWidgets, overallYpos);
		}

	}

	private void hideListItems(WidgetInfo wi)
	{
		client.getWidget(wi).setHidden(true);

		for (Widget w : client.getWidget(wi).getChildren())
		{
			w.setHidden(true);
		}
	}

	private void showListItems(WidgetInfo wi, LinkedHashMap<Widget, Quest> widgetList)
	{
		client.getWidget(wi).setHidden(false);

		for (Widget w : client.getWidget(wi).getChildren())
		{
			w.setHidden(false);

			String widgetText = w.getText();

			// remove any additions that was previously added
			if (w.getText().contains(">"))
			{
				widgetText = widgetText.substring(widgetText.lastIndexOf(">") + 2, widgetText.length());
				w.setText(widgetText);
			}

			Quest q = Quest.getQuest(w.getText());

			if (q != null)
			{
				widgetList.put(w, q);
			}
			// in case a new quest is added but it is not yet recognized by the Quest class
			// !w.getName().equals("") prevents quest list titles (Free, Members', Miniquests) from getting through
			else if (!w.getName().equals(""))
			{
				widgetList.put(w, Quest.UNKNOWN);
			}
		}
	}

	private int processQuestListItems(WidgetInfo wi, LinkedHashMap<Widget, Quest> widgetList, int overallYpos)
	{
		if (client.getWidget(wi) != null && client.getWidget(wi).getChildren() != null)
		{
			client.getWidget(wi).setOriginalY(overallYpos);
			client.getWidget(wi).setRelativeY(overallYpos);
			overallYpos += QUEST_LIST_TITLE_SPACE;
			int listItemYpos = QUEST_LIST_TITLE_SPACE;

			for (Widget w : widgetList.keySet())
			{
				if (w != null && widgetList.get(w) != null)
				{
					Quest q = widgetList.get(w);

					if (filterQuest(q, w))
					{
						w.setHidden(true);
					}
					else
					{
						w.setOriginalY(listItemYpos);
						w.setRelativeY(listItemYpos);
						listItemYpos += QUEST_LIST_ITEM_SPACE;
						overallYpos += QUEST_LIST_ITEM_SPACE;
					}

					if (config.showLength() || config.showDifficulty())
					{
						updateText(q, w);
					}
				}
			}
		}

		return overallYpos;
	}

	private boolean filterQuest(Quest q, Widget w)
	{
		switch (w.getTextColor())
		{
			case COMPLETE_COLOR:
				if (config.hideCompleted()) return true;
				break;
			case IN_PROGRESS_COLOR:
				if (config.hideInProgress()) return true;
				break;
			case NOT_STARTED_COLOR:
				if (config.hideNotStarted()) return true;
				break;
		}

		return config.hideCantDo() && !q.getQuestRequirement().isMet(this, client);
	}

	private LinkedHashMap<Widget, Quest> sortListItems(WidgetInfo wi, LinkedHashMap<Widget, Quest> widgetList)
	{
		// alphabetical is natural quest list sort order
		// sorting alphabetically first will keep quest list order natural with respect to any other sorts
		widgetList = sortQuest(widgetList, SortType.ALPHABETICAL);

		if (config.sortLength())
		{
			widgetList = sortQuest(widgetList, SortType.LENGTH);
			rearrangeWidgets(widgetList);
		}

		if (config.sortDifficulty())
		{
			widgetList = sortQuest(widgetList, SortType.DIFFICULTY);
			rearrangeWidgets(widgetList);
		}

		return widgetList;
	}

	private void updateText(Quest q, Widget w)
	{
		StringBuilder s = new StringBuilder(w.getText());

		if (config.showLength())
		{
			// put a colored letter at the beginning of the quest list item text to signify quest length
			// S - Green - short
			// M - Yellow - medium
			// L - Orange - long
			// V - Red - very long
			switch (q.getQuestLength())
			{
				case SHORT:
					s.insert(0, "<col=DC10D>S</col> ");
					break;
				case MEDIUM:
					s.insert(0, "<col=FFFF00>M</col> ");
					break;
				case LONG:
					s.insert(0, "<col=FF8C00>L</col> ");
					break;
				case VERY_LONG:
					s.insert(0, "<col=FF0000>V</col> ");
					break;
			}
		}

		if (config.showDifficulty())
		{
			// put a colored letter at the beginning of the quest list item text to signify quest difficulty
			// N - Green - novice
			// I - Yellow-Green - intermediate
			// E - Yellow - experienced
			// M - Orange - master
			// G - Red - grandmaster
			switch (q.getQuestDifficulty())
			{
				case NOVICE:
					s.insert(0, "<col=DC10D>N</col> ");
					break;
				case INTERMEDIATE:
					s.insert(0, "<col=9ACD32>I</col> ");
					break;
				case EXPERIENCED:
					s.insert(0, "<col=FFFF00>E</col> ");
					break;
				case MASTER:
					s.insert(0, "<col=FF8C00>M</col> ");
					break;
				case GRANDMASTER:
					s.insert(0, "<col=FF0000>G</col> ");
					break;
			}
		}

		w.setText(s.toString());
	}

	private void rearrangeWidgets(LinkedHashMap<Widget, Quest> l)
	{
		int yPos = QUEST_LIST_TITLE_SPACE;

		for (Widget w : l.keySet())
		{
			w.setRelativeY(yPos);
			w.setOriginalY(yPos);
			yPos += QUEST_LIST_ITEM_SPACE;
		}
	}

	private static LinkedHashMap<Widget, Quest> sortQuest(HashMap<Widget, Quest> unsortedQuest, SortType s)
	{
		List<HashMap.Entry<Widget, Quest>> list = new ArrayList<>(unsortedQuest.entrySet());

		switch (s)
		{
			case ALPHABETICAL:
				// natural order is alphabetical, so sorting by relative y preserves natural/alphabetical order
				list.sort(Comparator.comparing(a -> a.getKey().getRelativeY()));
				break;
			case DIFFICULTY:
				list.sort(Comparator.comparing(a -> a.getValue().getQuestDifficulty()));
				break;
			case LENGTH:
				list.sort(Comparator.comparing(a -> a.getValue().getQuestLength()));
				break;
		}

		LinkedHashMap<Widget, Quest> result = new LinkedHashMap<>();

		for (HashMap.Entry<Widget, Quest> entry : list)
		{
			result.put(entry.getKey(), entry.getValue());
		}

		return result;
	}

	@Subscribe
	public void onMenuEntryAdded(MenuEntryAdded event)
	{
		if (!config.menuOption())
		{
			return;
		}

		int groupId = event.getActionParam1();
		String option = event.getOption();

		if (groupId == WidgetInfo.FIXED_VIEWPORT_QUESTS_TAB.getId() ||
			groupId == WidgetInfo.RESIZABLE_VIEWPORT_QUESTS_TAB.getId() ||
			groupId == WidgetInfo.RESIZABLE_VIEWPORT_BOTTOM_LINE_QUEST_TAB.getId())
		{
			insertMenuEntry(event, RESET);
			if (config.sortLength()) insertMenuEntry(event, UNSORT_BY_LENGTH);
			else insertMenuEntry(event, SORT_BY_LENGTH);
			if (config.showLength()) insertMenuEntry(event, HIDE_LENGTH);
			else insertMenuEntry(event, SHOW_LENGTH);
			if (config.sortDifficulty()) insertMenuEntry(event, UNSORT_BY_DIFFICULTY);
			else insertMenuEntry(event, SORT_BY_DIFFICULTY);
			if (config.showDifficulty()) insertMenuEntry(event, HIDE_DIFFICULTY);
			else insertMenuEntry(event, SHOW_DIFFICULTY);
			if (config.hideCantDo()) insertMenuEntry(event, SHOW_CANT_DO);
			else insertMenuEntry(event, HIDE_CANT_DO);
			if (config.hideNotStarted()) insertMenuEntry(event, SHOW_NOT_STARTED);
			else insertMenuEntry(event, HIDE_NOT_STARTED);
			if (config.hideInProgress()) insertMenuEntry(event, SHOW_IN_PROGRESS);
			else insertMenuEntry(event, HIDE_IN_PROGRESS);
			if (config.hideCompleted()) insertMenuEntry(event, SHOW_COMPLETE);
			else insertMenuEntry(event, HIDE_COMPLETE);
			if (config.hideMiniquests()) insertMenuEntry(event, SHOW_MINIQUESTS);
			else insertMenuEntry(event, HIDE_MINIQUESTS);
			if (config.hideMembers()) insertMenuEntry(event, SHOW_MEMBERS);
			else insertMenuEntry(event, HIDE_MEMBERS);
			if (config.hideFree()) insertMenuEntry(event, SHOW_FREE);
			else insertMenuEntry(event, HIDE_FREE);
		}
	}

	private void insertMenuEntry(MenuEntryAdded event, String newEntryOption)
	{
		MenuEntry[] options = client.getMenuEntries();

		for (MenuEntry option : options)
		{
			if (option.getOption().equals(newEntryOption))
			{
				return;
			}
		}

		final MenuEntry entry = new MenuEntry();
		entry.setOption(newEntryOption);
		entry.setTarget(event.getTarget());
		entry.setType(MenuAction.RUNELITE.getId());
		entry.setParam0(event.getActionParam0());
		entry.setParam1(event.getActionParam1());

		options = Arrays.copyOf(options, options.length + 1);
		options[options.length - 1] = entry;
		// keep cancel as the last option
		ArrayUtils.swap(options, options.length - 1, options.length - 2);
		client.setMenuEntries(options);
	}

	@Subscribe
	public void onFilterMenuClicked(PlayerMenuOptionClicked event)
	{
		switch (event.getMenuOption())
		{
			case SHOW_FREE:
				configManager.setConfiguration(CONFIG_GROUP, "hideFree", false);
				break;
			case SHOW_MEMBERS:
				configManager.setConfiguration(CONFIG_GROUP, "hideMembers", false);
				break;
			case SHOW_MINIQUESTS:
				configManager.setConfiguration(CONFIG_GROUP, "hideMiniquests", false);
				break;
			case SHOW_COMPLETE:
				configManager.setConfiguration(CONFIG_GROUP, "hideCompleted", false);
				break;
			case SHOW_IN_PROGRESS:
				configManager.setConfiguration(CONFIG_GROUP, "hideInProgress", false);
				break;
			case SHOW_NOT_STARTED:
				configManager.setConfiguration(CONFIG_GROUP, "hideNotStarted", false);
				break;
			case SHOW_CANT_DO:
				configManager.setConfiguration(CONFIG_GROUP, "hideCantDo", false);
				break;
			case HIDE_DIFFICULTY:
				configManager.setConfiguration(CONFIG_GROUP, "showDifficulty", false);
				break;
			case UNSORT_BY_DIFFICULTY:
				configManager.setConfiguration(CONFIG_GROUP, "sortDifficulty", false);
				break;
			case HIDE_LENGTH:
				configManager.setConfiguration(CONFIG_GROUP, "showLength", false);
				break;
			case UNSORT_BY_LENGTH:
				configManager.setConfiguration(CONFIG_GROUP, "sortLength", false);
				break;
			case HIDE_FREE:
				configManager.setConfiguration(CONFIG_GROUP, "hideFree", true);
				break;
			case HIDE_MEMBERS:
				configManager.setConfiguration(CONFIG_GROUP, "hideMembers", true);
				break;
			case HIDE_MINIQUESTS:
				configManager.setConfiguration(CONFIG_GROUP, "hideMiniquests", true);
				break;
			case HIDE_COMPLETE:
				configManager.setConfiguration(CONFIG_GROUP, "hideCompleted", true);
				break;
			case HIDE_IN_PROGRESS:
				configManager.setConfiguration(CONFIG_GROUP, "hideInProgress", true);
				break;
			case HIDE_NOT_STARTED:
				configManager.setConfiguration(CONFIG_GROUP, "hideNotStarted", true);
				break;
			case HIDE_CANT_DO:
				configManager.setConfiguration(CONFIG_GROUP, "hideCantDo", true);
				break;
			case SHOW_DIFFICULTY:
				configManager.setConfiguration(CONFIG_GROUP, "showDifficulty", true);
				break;
			case SORT_BY_DIFFICULTY:
				configManager.setConfiguration(CONFIG_GROUP, "sortDifficulty", true);
				break;
			case SHOW_LENGTH:
				configManager.setConfiguration(CONFIG_GROUP, "showLength", true);
				break;
			case SORT_BY_LENGTH:
				configManager.setConfiguration(CONFIG_GROUP, "sortLength", true);
				break;
			case RESET:
				configManager.setDefaultConfiguration(config, true);
				break;
		}
	}

}
