# WorkMeter SOLO 2.7.5 — five-minute ceiling

Confirmed rule: closing at 17:32 records 17:35, and reported work duration rounds up to a multiple of five minutes. An exact boundary is unchanged. Start timestamps remain unchanged.

Closing uses the real timestamp for clock/stage validation, stores it as `recordedEnd`, and saves the rounded `end` plus the same final-stage boundary. GPS stops immediately when the user confirms; rounding does not delay the stop action. Existing overlap validation remains enabled. A rounded end can fall after midnight or temporarily be in the future, as requested. Starting another overlapping shift is prevented by existing integrity checks.

Mój czas summaries, day cards, shift rows/details and SMS totals use five-minute ceilings. Historical data is not rewritten: existing end times are rounded when displayed in these reports; exact stage history and live clocks are retained. Aggregates are rounded independently after personal-time exclusions and overlap deduplication. This is not per-stage rounding. Existing exports/financial calculations continue to use stored records, so historical raw records retain their previous accounting values; newly closed shifts use their stored rounded end. Manual shift edits round the selected ending too, and a user-picked closing time clears hidden seconds.

Validation added: eight JVM boundary/duration cases and two Android flows covering the reported 09:04 regression, summary/day/row agreement, preservation of historical timestamps, confirmed stop, matching stage boundary, actual click audit and GPS stop.
