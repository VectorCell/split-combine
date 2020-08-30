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
	echo -e "\t\tlotus"
	echo
	exit 1
fi

TEMPDIR=$(mktemp -d /tmp/split-combine.XXXXXX)
cd $TEMPDIR

function clean_exit () {
	pkill -P $$
	cd /
	rm -rf $TEMPDIR
	exit
}
trap clean_exit SIGINT

SPLIT_COMBINE="$DIR/split-combine"

MODE="$1"
N_STREAMS="2"

if [ "$MODE" == "-l" ]; then

	# listen mode

	N_STREAMS="$2"
	if [ -z "$N_STREAMS" ]; then
		N_STREAMS="2"
	fi

	if [ "$N_STREAMS" == "2" ]; then
		mkfifo a b
		nc -l 8801 > a &
		nc -l 8802 > b &
		# echo "listening on ports 8801, 8802" 1>&2
		$SPLIT_COMBINE -c a b
	elif [ "$N_STREAMS" == "3" ]; then
		mkfifo a b c
		nc -l 8801 > a &
		nc -l 8802 > b &
		nc -l 8803 > c &
		# echo "listening on ports 8801, 8802, 8803" 1>&2
		$SPLIT_COMBINE -c a b c
	else
		echo "ERROR: unsupported number of streams: $N_STREAMS" 1>&2
		clean_exit
	fi

	wait

	cd /tmp
	rm -rf $TEMPDIR
	clean_exit

elif [ "$MODE" == "-s" ]; then
	DEST="$2"
	N_STREAMS="$3"
else
	DEST="$1"
	N_STREAMS="$2"
fi


# defaults to 2
if [ -z "$N_STREAMS" ]; then
	N_STREAMS=2
fi


# IP_A is on the first auxiliary subnet  172.16.1.0/24
# IP_B is on the second auxiliary subnet 172.16.2.0/24
# IP_C is on the primary network, subnet 10.0.0.0/16
if [ "$DEST" == "null" ]; then
	echo "NOT SENDING ANYWHERE" 1>&2
	cat > /dev/null
elif [ "$DEST" == "green" ]; then
	IP_A=172.16.1.1
	IP_B=172.16.2.1
	IP_C=10.0.1.100
elif [ "$DEST" == "tank" ]; then
	IP_A=172.16.1.2
	IP_B=172.16.2.2
	IP_C=10.0.1.101
elif [ "$DEST" == "nissan" ]; then
	IP_A=172.16.1.3
	IP_B=172.16.2.3
	IP_C=10.0.1.102
elif [ "$DEST" == "lotus" ]; then
	IP_A=172.16.1.4
	IP_B=172.16.2.4
	IP_C=10.0.1.105
else
	echo "ERROR: unknown destination: $DEST" 1>&2
	clean_exit
fi


if [ "$N_STREAMS" == "3" ] && [ "$IP_B" == "null" ]; then
	echo "ERROR: unsupported configuration!" 1>&2
	echo "       this host ($HOSTNAME) only supports 2 concurrent streams" 1>&2
	clean_exit
fi
if [ "$HOSTNAME" == "lotus" ]; then
	# for machines without a connection to the second auxiliary subnet
	IP_B=$IP_C
fi


# now we can finally start sending data
if [ "$N_STREAMS" == "2" ]; then
	# echo "IP_A: $IP_A"
	# echo "IP_B: $IP_B"
	mkfifo a b
	nc -N $IP_A 8801 < a &
	nc -N $IP_B 8802 < b &
	$SPLIT_COMBINE -s a b
	wait
elif [ "$N_STREAMS" == "3" ]; then
	# echo "IP_A: $IP_A"
	# echo "IP_B: $IP_B"
	# echo "IP_C: $IP_C"
	mkfifo a b c
	nc -N $IP_A 8801 < a &
	nc -N $IP_B 8802 < b &
	nc -N $IP_C 8803 < c &
	$SPLIT_COMBINE -s a b c
	wait
else
	echo "ERROR: unknown number of streams: $N_STREAMS" 1>&2
	echo "THIS IS A LOGIC ERROR" 1>&2
	clean_exit
fi


clean_exit
