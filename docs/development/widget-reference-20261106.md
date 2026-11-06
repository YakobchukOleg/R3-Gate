# WorkMeter SOLO 2.7.3 — reference widget

Base: verified 2.7.2 `d64dac5`. New branch; main unchanged.

Implement the owner's second reference image with native launcher RemoteViews: compact 180×76dp, medium 300×80dp, large 300×216dp. Slate widget colors are sampled from the supplied reference: background #4F4F4D, secondary tiles #3C3D38, primary stop #C04A58, foreground #F5F4EF. Reference-sized large layout uses 14dp outer padding, 48dp primary/camera row, 8dp gaps and 56dp icon-over-caption secondary tiles. Medium controls are 48dp switch and 110×48dp stop; compact control is a 44dp circle within a 48dp touch target. Building, side-view car, cart, camera, switch and stop are consistent outlined vectors. Nordic and dark variants remain available.

Large time hierarchy follows the reference: prominent live stage chronometer, smaller live daily work total labelled Dziś. Native chronometer formatting is retained so timers keep updating in the launcher without introducing an extra background service. Stop/start confirmation, change-object, travel, shopping, camera and root-open PendingIntents are preserved. Dense launcher boxes and 200% text adapt spacing and icon/caption presence; reference geometry is exact at the specified reference sizes, not every launcher grid.

Cloud checkmark is shown only for the last verified Drive backup belonging to the connected account; absent verification uses a neutral cloud outline. It is not a claim that all current records have been uploaded. Preview background no longer has the blue-gray frame from the phone screenshot. Existing per-instance settings, background-only transparency and opaque readable foreground remain.

Validation added: 5 JVM cloud-evidence cases, 4 Android reference geometry/timer/render cases, updated the superseded timer hierarchy assertion. Existing multi-size/theme/state/font-scale and real AppWidgetHost tests remain enabled. CI exports actual reference PNGs for visual inspection. Physical Samsung launcher sizing still needs phone acceptance.
