/*
**  output-sparse.cc
**
**  Author: Brandon Smith
*/

#include "split-combine.h"

#include <iostream>
#include <vector>
#include <string>
#include <sstream>
#include <atomic>

#include <cstdio>
#include <cstdint>
#include <cstring>

#include <unistd.h>
#include <inttypes.h>
#include <signal.h>

using namespace std;

const size_t BLOCKSIZE = 4096;

atomic<bool> stopped(false);
bool verbose = false;

enum Mode {UNSET, SPLIT, COMBINE};

void sighandler (int signo)
{
	if (signo == SIGINT || signo == SIGTERM) {
		stopped = true;
	}
}

bool is_blk_zeroed (const void *block, size_t size)
{
	const uint8_t *ptr = static_cast<const uint8_t*>(block);
	for (size_t k = 0; k < size; ++k) {
		if (ptr[k] != 0) {
			return false;
		}
	}
	return true;
}

bool parse_args (int argc, char *argv[], Mode& mode, vector<string>& filenames)
{
	int c;
	while ((c = getopt(argc, argv, "vsc")) != -1) {
		switch (c) {
			case 'v':
				verbose = true;
				break;
			case 's':
				cerr << "using split mode" << endl;
				mode = SPLIT;
				break;
			case 'c':
				cerr << "using combine mode" << endl;
				mode = COMBINE;
				break;
			default:
				fprintf(stderr, "unknown arg: %c\n", c);
				return false;
		}
	}

	for (int k = optind; k < argc; ++k) {
		cerr << "argv[" << k << "] :: " << argv[k] << endl;
		filenames.push_back(argv[k]);
	}

	return true;
}

int mode_split (const vector<string>& filenames) {

	FILE *infile = stdin;
	vector<FILE*> outfiles;
	for (const string& filename : filenames) {
		FILE *f = fopen(filename.c_str(), "wb");
		if (f == nullptr) {
			cerr << "ERROR: unable to open file: " << filename << endl;
			return EXIT_FAILURE;
		}
		// cerr << "opened file " << filename << " for output" << endl;
		outfiles.push_back(f);
	}

	// cerr << "stdin:   " << stdin << endl;
	// cerr << "stdout:  " << stdout << endl;
	// cerr << "infile:  " << infile << endl;
	// cerr << "outfile: " << outfiles[0] << endl;

	uint8_t block[BLOCKSIZE];

	// first, send a control block
	// currently, this is in the form of a string which can be parsed
	for (int k = 0; k < outfiles.size(); ++k) {
		// fprintf(stderr, "index %d\nn_streams %ld\n", k, outfiles.size());
		memset(block, 0, BLOCKSIZE);
		sprintf(reinterpret_cast<char*>(block), "index %d n_streams %ld\n", k, outfiles.size());
		fwrite(block, 1, BLOCKSIZE, outfiles[k]);
	}

	size_t n_bytes = 0;
	size_t count = 0;
	int idx = 0;
	while ((count = fread(block, 1, BLOCKSIZE, infile)) > 0) {
		n_bytes += count;
		fwrite(block, 1, count, outfiles[idx]);
		++idx;
		if (idx == outfiles.size())
			idx = 0;
	}

	cerr << "split a total of " << n_bytes << " bytes" << endl;

	for (FILE*& f : outfiles) {
		fclose(f);
	}

	return EXIT_SUCCESS;
}

int mode_combine (const vector<string>& filenames) {
	FILE *outfile = stdout;
	vector<FILE*> infiles;
	for (const string& filename : filenames) {
		FILE *f = fopen(filename.c_str(), "rb");
		if (f == nullptr) {
			cerr << "ERROR: unable to open file: " << filename << endl;
			return EXIT_FAILURE;
		}
		infiles.push_back(f);
	}

	uint8_t block[BLOCKSIZE];
	size_t count = 0;
	size_t n_bytes = 0;

	vector<FILE*> infiles_sorted(infiles.size());

	// first, read the control block(s)
	for (FILE*& infile : infiles) {
		if ((count = fread(block, 1, BLOCKSIZE, infile)) > 0) {
			istringstream iss(reinterpret_cast<char*>(block));
			int index = -1;
			int n_streams = -1;
			while (iss) {
				string label;
				int value;
				iss >> label >> value;
				if (label == "index") {
					index = value;
				} else if (label == "n_streams") {
					n_streams = value;
				} else if (label == "") {
					continue;
				}
				cerr << "found label " << label << " with value " << value << endl;
			}
			if (index < 0 || n_streams < 0) {
				cerr << "ERROR: unable to parse control block!" << endl;
				return EXIT_FAILURE;
			}
			if (n_streams != infiles_sorted.size()) {
				cerr << "ERROR: control block reports " << n_streams 
					<< " streams, but there are " << infiles.size() << " files!" << endl;
				return EXIT_FAILURE;
			}
			infiles_sorted[index] = infile;
		} else {
			cerr << "unable to read control block!" << endl;
			return EXIT_FAILURE;
		}
	}

	int idx = 0;
	while ((count = fread(block, 1, BLOCKSIZE, infiles_sorted[idx]))) {
		n_bytes += count;
		fwrite(block, 1, count, outfile);
		++idx;
		if (idx == infiles_sorted.size())
			idx = 0;
	}

	cerr << "combined a total of " << n_bytes << " bytes" << endl;

	for (FILE*& f : infiles) {
		fclose(f);
	}

	return EXIT_SUCCESS;
}

int main (int argc, char *argv[])
{
	Mode mode = UNSET;
	vector<string> filenames;

	if (!parse_args(argc, argv, mode, filenames))
		return EXIT_FAILURE;

	// if (signal(SIGINT, sighandler) == SIG_ERR) {
	// 	fprintf(stderr, "ERROR: unable to set handler for SIGINT.\n");
	// 	return EXIT_FAILURE;
	// }
	// if (signal(SIGTERM, sighandler) == SIG_ERR) {
	// 	fprintf(stderr, "ERROR: unable to set handler for SIGTERM.\n");
	// 	return EXIT_FAILURE;
	// }

	int retval = EXIT_SUCCESS;
	switch (mode) {
		case SPLIT:
			cerr << "mode: SPLIT" << endl;
			retval = mode_split(filenames);
			break;
		case COMBINE:
			cerr << "mode: COMBINE" << endl;
			retval = mode_combine(filenames);
			break;
		case UNSET:
			cerr << "ERROR: mode not set!" << endl;
			retval = EXIT_FAILURE;
		default:
			cerr << "ERROR: unknown mode!" << endl;
			retval = EXIT_FAILURE;
	}
	cerr << "exiting with code " << retval << endl;
	return retval;
}
