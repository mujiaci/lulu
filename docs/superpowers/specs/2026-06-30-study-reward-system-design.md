# Study Reward System Design

## Goal

Build Lulu's built-in postgraduate-study reward system as a complete first version: daily tasks, pomodoro rewards, kudos currency, mystery boxes, gacha, collections, achievements, shop, super moment, and companion feedback.

## Scope

The system lives inside the existing `Screen.Study` and `StudyPomodoro` routes. It keeps the current character companionship path, but replaces the old temporary study page with a persistent reward system.

## Architecture

- `data/study/StudyModels.kt`: serializable state, rewards, inventory, cards, achievements, shop items, and events.
- `data/study/StudyRules.kt`: pure Kotlin reward engine for sign-in, tasks, pomodoro mystery boxes, gacha, levels, achievements, shop refresh, and penalties.
- `data/study/StudyStore.kt`: DataStore-backed persistence for `StudyState`.
- `ui/pages/study/StudyVM.kt`: state reducer and UI actions.
- `ui/pages/study/StudyPage.kt`: complete Compose experience for dashboard, tasks, gacha, collection, achievements, shop, pomodoro setup, and focus mode.

## UX Direction

The page should feel encouraging rather than clinical: warm daylight, soft blue, small gold accents, celebratory reward feedback, and companion copy. It should show progress and possibility at first glance: today's progress, kudos, level, sign-in, a companion card, and quick actions for pomodoro and gacha.

## Rewards

The first version implements the full supplied economy:

- Single draw costs 100 kudos.
- Ten draw costs 800 kudos.
- Sign-in gives 25, with day 3 giving 50 and day 5+ giving 75.
- Task completion gives 100.
- Pomodoro completion gives 50 plus a weighted mystery box.
- Inactivity penalties are computed with a floor of 0.
- Super moment triggers at 100% daily task completion and grants one selectable bonus, one ten-draw ticket, and 200 kudos.
- Gacha odds are normal 85%, rare 12%, epic 3%.
- Normal fragments collect 10 outfits x 6 parts x 4 fragments.
- Rare fragments collect 12 theaters x 5 fragments.
- Epic fragments are McDonald's fragments; 2 fragments can be redeemed once.
- Level and achievement rewards follow the provided table.
- Mystery shop refreshes 3 daily items.

## Companion Integration

The first version writes study events locally and uses the current assistant on the study and pomodoro pages for feedback. Formal memory-bank insertion and proactive-message scheduling are left as follow-up wiring, but the event model is already shaped for that connection.
