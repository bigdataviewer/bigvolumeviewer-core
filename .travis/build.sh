#!/bin/sh
curl -fsLO https://raw.githubusercontent.com/scijava/scijava-scripts/master/travis-build.sh
sh travis-build.sh $encrypted_21026188877a_key $encrypted_21026188877a_iv
