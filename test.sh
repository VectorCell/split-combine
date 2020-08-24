#!/bin/bash

tmp_sm_0=$(mktemp /tmp/split-combine.XXXXXX)
tmp_sm_1=$(mktemp /tmp/split-combine.XXXXXX)
tmp_lg_0=$(mktemp /tmp/split-combine.XXXXXX)
tmp_lg_1=$(mktemp /tmp/split-combine.XXXXXX)

echo
echo "echo hello world | ./split-combine -s $tmp_sm_0 $tmp_sm_1"
echo hello world | ./split-combine -s $tmp_sm_0 $tmp_sm_1

echo
echo "./split-combine -c $tmp_sm_0 $tmp_sm_1"
./split-combine -c $tmp_sm_0 $tmp_sm_1

echo
echo "creating large temp file ..."
dd if=/dev/urandom of=$tmp_lg_0 bs=4k count=1k status=progress

echo
echo "./split-combine -s $tmp_sm_0 $tmp_sm_1 < $tmp_lg_0"
./split-combine -s $tmp_sm_0 $tmp_sm_1 < $tmp_lg_0

echo
echo "./split-combine -c $tmp_sm_0 $tmp_sm_1"
./split-combine -c $tmp_sm_0 $tmp_sm_1 > $tmp_lg_1

echo
md5sum $tmp_lg_0 $tmp_lg_1

# echo
# echo "tmp_sm_0:"
# cat $tmp_sm_0
# echo "tmp_sm_1:"
# cat $tmp_sm_1


# dd if=/dev/zero bs=4k count=1k 2> /dev/null \
# 	| ./split-combine -s > /dev/null && echo "exited with code $?"

echo
echo "waiting for children ..."
wait

rm -f $tmp_sm_0 $tmp_sm_1 $tmp_lg_0 $tmp_lg_1
