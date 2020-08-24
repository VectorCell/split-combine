CSTD   := c11
CPPSTD := c++11

ifeq "$(CXX)" "g++"
	GCCVERSIONLT48 := $(shell expr `gcc -dumpversion` \< 4.8)
	ifeq "$(GCCVERSIONLT48)" "1"
		CC  := gcc-4.8
		CXX := g++-4.8
	endif
endif

CFLAGS   := -pedantic -std=$(CSTD) -O3
CPPFLAGS := -pedantic -std=$(CPPSTD) -O3
LIBFLAGS := 

all: split-combine

split-combine : split-combine.cc
	$(CXX) $(CPPFLAGS) -o split-combine split-combine.cc $(LIBFLAGS)

test: all
	./test.sh

install: all
	./install.sh

uninstall:
	rm $(which sc)
	rm $(which split-combine)

clean :
	rm -f *.o
	rm -f *.d
	rm -f *.elf
	rm -f split-combine
	rm -rf /tmp/split-combine*

-include *.d
