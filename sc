#!/bin/bash

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"

if [ -z "$1" ]; then
	echo "Usage:"
	echo
	echo "LISTEN MODE:"
	echo
	echo -e "\tsc -l [N_STREAMS]"
	echo
	echo -e "\tThe default value of N_STREAMS is 2."
	echo -e "\tThis must match the number of streams in the configuration"
	echo -e "\tthat the sender is using."
	echo
	echo "SEND MODE:"
	echo
	echo -e "\tsc DEST"
	echo
	echo -e "\tDEST must be the name of a valid configuration in this script."
	echo -e "\tValid configurations are:"
	echo -e "\t\tgreen"
	echo -e "\t\tgreen-3"
	echo -e "\t\ttank"
	echo -e "\t\ttank-3"
	echo -e "\t\tnissan"
	echo -e "\t\tnissan-3"
	echo -e "\t\tprecisix"
	echo
	exit 1
fi

TEMPDIR=$(mktemp -d /tmp/split-combine.XXXXXX)
cd $TEMPDIR

function clean_exit () {
	# maybe can also do this?
	pkill -P $$
	# for PID in $(jobs -p); do
	# 	kill -9 $PID 2> /dev/null
	# done
	cd /
	rm -rf $TEMPDIR
	exit
}
trap clean_exit SIGINT

SPLIT_COMBINE="$DIR/split-combine"

MODE="$1"

if [ "$MODE" == "-l" ]; then

	N_STREAMS="$2"
	if [ -z "$N_STREAMS" ]; then
		N_STREAMS=2
	fi

	if [ "$N_STREAMS" == "2" ]; then
		mkfifo a b
		nc -l 8801 > a &
		nc -l 8802 > b &
		$SPLIT_COMBINE -c a b
	elif [ "$N_STREAMS" == "3" ]; then
		mkfifo a b c
		nc -l 8801 > a &
		nc -l 8802 > b &
		nc -l 8803 > c &
		$SPLIT_COMBINE -c a b c
	else
		echo "ERROR: unsupported number of streams: $N_STREAMS" 1>%2
		clean_exit
	fi

	wait

	cd /tmp
	rm -rf $TEMPDIR
	clean_exit

elif [ "$MODE" == "-s" ]; then
	DEST="$2"
else
	DEST="$MODE"
fi


if [ "$DEST" == "null" ]; then
	echo "NOT SENDING ANYWHERE" 1>&2
	cat > /dev/null

elif [ "$DEST" == "green" ]; then
	mkfifo a b
	nc -N 172.16.1.1 8801 < a &
	nc -N 172.16.2.1 8802 < b &
	$SPLIT_COMBINE -s a b

elif [ "$DEST" == "tank" ]; then
	mkfifo a b
	nc -N 172.16.1.2 8801 < a &
	nc -N 172.16.2.2 8802 < b &
	$SPLIT_COMBINE -s a b

elif [ "$DEST" == "nissan" ]; then
	mkfifo a b
	nc -N 172.16.1.3 8801 < a &
	nc -N 172.16.2.3 8802 < b &
	$SPLIT_COMBINE -s a b

elif [ "$DEST" == "precisix" ]; then
	mkfifo a b
	nc -N 172.16.1.4 8801 < a &
	nc -N 10.0.1.103 8802 < b &
	$SPLIT_COMBINE -s a b

elif [ "$DEST" == "green-3" ]; then
	mkfifo a b c
	nc -N 172.16.1.1 8801 < a &
	nc -N 172.16.2.1 8802 < b &
	nc -N 10.0.1.100 8803 < c &
	$SPLIT_COMBINE -s a b c

elif [ "$DEST" == "tank-3" ]; then
	mkfifo a b c
	nc -N 172.16.1.2 8801 < a &
	nc -N 172.16.2.2 8802 < b &
	nc -N 10.0.1.101 8803 < c &
	$SPLIT_COMBINE -s a b c

elif [ "$DEST" == "nissan-3" ]; then
	mkfifo a b c
	nc -N 172.16.1.3 8801 < a &
	nc -N 172.16.2.3 8802 < b &
	nc -N 10.0.1.102 8803 < c &
	$SPLIT_COMBINE -s a b c

else
	echo "ERROR: unknown destination: $DEST" 1>&2
	clean_exit
fi

clean_exit
