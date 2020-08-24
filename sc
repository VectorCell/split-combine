#!/bin/bash

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"
SPLIT_COMBINE="$DIR/split-combine"

MODE="$1"

if [ "$MODE" == "-l" ]; then
	# N_STREAMS="$2"
	# if [ -z "$N_STREAMS" ]; then
	# 	echo "ERROR: must specify number of streams!" 1>&2
	# 	exit 1
	# fi

	TEMPDIR=$(mktemp -d /tmp/split-combine.XXXXXX)
	cd $TEMPDIR

	mkfifo a b
	nc -l 8801 > a &
	nc -l 8802 > b &

	$SPLIT_COMBINE -c a b

	cd /tmp
	rm -rf $TEMPDIR
elif [ "$MODE" == "-s" ]; then

	TEMPDIR=$(mktemp -d /tmp/split-combine.XXXXXX)
	cd $TEMPDIR

	mkfifo a b
	nc -N 127.0.0.1 8801 < a &
	nc -N 127.0.0.1 8802 < b &

	PORT=8801
	for ARG in "$@"; do
		if [ "$ARG" == "$MODE"]; then
			continue
		fi
		mkfifo fifo-$PORT
		nc -N $ARG $PORT < fifo-$PORT &
		((PORT=PORT+1))
	done

	$SPLIT_COMBINE -s a b

	wait

	cd /tmp
	rm -rf $TEMPDIR
else
	echo "ERROR: unrecognized mode: $MODE" 1>&2
	exit 1
fi
