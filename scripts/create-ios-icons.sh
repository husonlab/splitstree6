#!/bin/bash

export src=" ../artwork/SplitsTree6-icon-1024x1024.png"
export dir="../src/ios/assets/Assets.xcassets/AppIcon.appiconset"


sips -z 40 40 $src --out $dir/splitstree-iphone-notification-icon-20@2x.png
sips -z 60 60 $src --out $dir/splitstree-iphone-notification-icon-20@3x.png
sips -z 48 48 $src --out $dir/splitstree-iphone-spotlight-settings-icon-29@2x.png
sips -z 87 87 $src --out $dir/splitstree-iphone-spotlight-settings-icon-29@3x.png
sips -z 80 80 $src --out $dir/splitstree-iphone-spotlight-icon-40@2x.png
sips -z 120 120 $src --out $dir/splitstree-iphone-spotlight-icon-40@3x.png
sips -z 120 120 $src --out $dir/splitstree-iphone-app-icon-60@2x.png
sips -z 180 180 $src --out $dir/splitstree-iphone-app-icon-60@3x.png
sips -z 20 20 $src --out $dir/splitstree-ipad-notifications-icon-20@1x.png
sips -z 40 40 $src --out $dir/splitstree-ipad-notifications-icon-20@2x.png
sips -z 29 29 $src --out $dir/splitstree-ipad-settings-icon-29@1x.png
sips -z 48 48 $src --out $dir/splitstree-ipad-settings-icon-29@2x.png
sips -z 40 40 $src --out $dir/splitstree-ipad-spotlight-icon-40@1x.png
sips -z 80 80 $src --out $dir/splitstree-ipad-spotlight-icon-40@2x.png
sips -z 76 76 $src --out $dir/splitstree-ipad-app-icon-76@1x.png
sips -z 152 152 $src --out $dir/splitstree-ipad-app-icon-76@2x.png
sips -z 167 167 $src --out $dir/splitstree-ipad-pro-app-icon-83.5@2x.png
sips -z 1024 1024 $src --out $dir/splitstree-app-store-icon-1024@1x.png


