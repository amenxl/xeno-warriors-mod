#!/bin/bash
# push current commit, run the selftest workflow, fetch results into /tmp/st
cd /home/claude/xeno-warriors-mod
gh api -X POST repos/amenxl/xeno-warriors-mod/actions/workflows/selftest.yml/dispatches -f ref=main
sleep 15
rid=$(gh api "repos/amenxl/xeno-warriors-mod/actions/workflows/selftest.yml/runs?per_page=1" --jq '.workflow_runs[0].id')
until s=$(gh api repos/amenxl/xeno-warriors-mod/actions/runs/$rid --jq '.status+" "+(.conclusion//"")'); [[ $s == completed* ]]; do sleep 20; done
echo "RUN $rid $s"
rm -rf /tmp/st; mkdir /tmp/st; cd /tmp/st
for n in $(gh api repos/amenxl/xeno-warriors-mod/releases/tags/selftest --jq '.assets[].name'); do
  u=$(gh api repos/amenxl/xeno-warriors-mod/releases/tags/selftest --jq ".assets[]|select(.name==\"$n\")|.url"); gh api -H "Accept: application/octet-stream" $u > $n
done
ls -la
grep -E "XW-SELFTEST|invalid dist|Caused by|Failed to create|Exception in" server.log | grep -v "QuestAvailabilityChecker\|setAccessible\|netty" | head -30
